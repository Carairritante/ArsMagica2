package am2.interop.voice;

import am2.LogHelper;
import am2.api.spell.ItemSpellBase;
import am2.api.spell.component.interfaces.ISpellComponent;
import am2.api.spell.component.interfaces.ISpellModifier;
import am2.api.spell.component.interfaces.ISpellShape;
import am2.spell.SpellUtils;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Word-indexed MFCC + Delta + Delta-Delta discriminative DTW acoustic engine (v5 / AMV5).
 *
 * Key upgrades over v4 (raw Log-Mel):
 * - 26 Mel filterbanks (100Hz-7200Hz) -> DCT-II keeping c1..c12 (dropping c0 energy) + sinusoidal liftering (L=22)
 * - Utterance-level CMVN + Delta (1st derivative) + Delta-Delta (2nd derivative) = 36-D phoneme-focused vectors
 * - Contiguous-island endpoint trimming (rejects isolated mouse clicks, pops and trailing breath noise)
 * - Tighter Sakoe-Chiba warping band (16%) + smooth duration-mismatch penalty
 * - Sample consistency guard (prevents corrupted recordings from poisoning a word's template bank)
 * - Multi-sample consensus scoring (prevents a single outlier sample from hijacking matches)
 */
public final class VoiceAcousticMatcher {

	private static final int FILE_MAGIC = 0x414D5635; // "AMV5"
	private static final int FILE_VERSION = 5;
	private static final int TARGET_SAMPLE_RATE = 16000;
	private static final int FRAME_SAMPLES = 400; // 25ms @ 16kHz
	private static final int FRAME_HOP = 160;     // 10ms @ 16kHz
	private static final int FFT_SIZE = 512;
	private static final int NUM_MEL_FILTERS = 26;
	private static final int NUM_CEPSTRA = 12;    // c1..c12 (c0 dropped to ignore overall loudness)
	private static final int FEATURE_DIM = NUM_CEPSTRA * 3; // 12 MFCC + 12 Delta + 12 Delta-Delta = 36
	private static final int MAX_SAMPLES_PER_WORD = 4;
	private static final int MIN_TRIMMED_FRAMES = 10;
	private static final int MAX_TRIMMED_FRAMES = 220;
	private static final double LIFTER_L = 22.0;
	private static final float DELTA_WEIGHT = 0.72f;
	private static final float DELTA2_WEIGHT = 0.36f;
	public static final double DEFAULT_MATCH_THRESHOLD = 0.52;
	public static final double DEFAULT_VAD_THRESHOLD = 0.020;

	private static final float[] HAMMING_WINDOW = buildHammingWindow(FRAME_SAMPLES);
	private static final int[] BAND_EDGES = buildMelBandEdges(NUM_MEL_FILTERS, TARGET_SAMPLE_RATE, FFT_SIZE, 100.0, 7200.0);
	private static final double[][] DCT_LIFTERED = buildDctLifteredMatrix(NUM_CEPSTRA, NUM_MEL_FILTERS, LIFTER_L);

	public static final class VoiceBinding {
		public final int vaultSlot; // resolved dynamically per world (-1 for standalone debug bindings)
		public String word;
		public String spellDisplayName;
		public String spellFingerprint;
		public final List<float[][]> samples = new ArrayList<float[][]>();

		public VoiceBinding(int vaultSlot, String word, String spellDisplayName, String spellFingerprint) {
			this.vaultSlot = vaultSlot;
			this.word = word;
			this.spellDisplayName = spellDisplayName;
			this.spellFingerprint = spellFingerprint;
		}
	}

	public static final class VaultCandidate {
		public final int slotIndex;
		public final String word;
		public final String spellLabel;

		public VaultCandidate(int slotIndex, String word, String spellLabel) {
			this.slotIndex = slotIndex;
			this.word = normalizeWord(word);
			this.spellLabel = spellLabel;
		}
	}

	public static final class MatchResult {
		public final VoiceBinding binding;
		public final double score;
		public final double secondBestScore;
		public final VoiceBinding secondBestBinding;
		public final boolean accepted;
		public final boolean ambiguous;

		public MatchResult(VoiceBinding binding, double score, VoiceBinding secondBestBinding,
						   double secondBestScore, boolean accepted, boolean ambiguous) {
			this.binding = binding;
			this.score = score;
			this.secondBestBinding = secondBestBinding;
			this.secondBestScore = secondBestScore;
			this.accepted = accepted;
			this.ambiguous = ambiguous;
		}
	}

	private final Map<String, VoiceBinding> wordTemplates = new LinkedHashMap<String, VoiceBinding>();
	private final File storageFile;

	private boolean prefContinuousListen = false;
	private double prefMatchThreshold = DEFAULT_MATCH_THRESHOLD;
	private double prefVadThreshold = DEFAULT_VAD_THRESHOLD;
	private boolean migratedFromLegacyFormat = false;

	public VoiceAcousticMatcher(File storageFile) {
		this.storageFile = storageFile;
		load();
	}

	public synchronized boolean consumeMigratedNotice() {
		boolean was = this.migratedFromLegacyFormat;
		this.migratedFromLegacyFormat = false;
		return was;
	}

	public synchronized boolean getPrefContinuousListen() {
		return prefContinuousListen;
	}

	public synchronized double getPrefMatchThreshold() {
		return prefMatchThreshold;
	}

	public synchronized double getPrefVadThreshold() {
		return prefVadThreshold;
	}

	public synchronized void savePreferences(boolean continuousListen, double matchThreshold, double vadThreshold) {
		this.prefContinuousListen = continuousListen;
		this.prefMatchThreshold = matchThreshold;
		this.prefVadThreshold = vadThreshold;
		save();
	}

	public synchronized int bindingCount() {
		return wordTemplates.size();
	}

	public synchronized List<VoiceBinding> listBindings() {
		return new ArrayList<VoiceBinding>(wordTemplates.values());
	}

	public synchronized VoiceBinding getBinding(String rawWord) {
		if (rawWord == null) return null;
		String norm = normalizeWord(rawWord);
		return norm.isEmpty() ? null : wordTemplates.get(norm);
	}

	public synchronized int getWordSampleCount(String rawWord) {
		VoiceBinding b = getBinding(rawWord);
		return b != null ? b.samples.size() : 0;
	}

	/**
	 * Checks whether a newly recorded feature matrix is clean and consistent with existing samples
	 * for the same word. Returns null if valid, or a user-facing rejection reason otherwise.
	 */
	public synchronized String validateSampleForWord(String rawWord, float[][] candidate, boolean forceReplace) {
		if (candidate == null || candidate.length < MIN_TRIMMED_FRAMES) {
			return "Fala muito curta ou ruído — tente falar novamente";
		}
		if (candidate.length > MAX_TRIMMED_FRAMES) {
			return "Gravação muito longa — fale apenas a palavra";
		}
		if (forceReplace) {
			return null;
		}
		VoiceBinding existing = getBinding(rawWord);
		if (existing == null || existing.samples.isEmpty()) {
			return null;
		}
		int[] lens = new int[existing.samples.size()];
		double bestSim = -1.0;
		for (int i = 0; i < existing.samples.size(); i++) {
			float[][] tpl = existing.samples.get(i);
			lens[i] = tpl.length;
			double sim = scoreSymmetricDtw(candidate, tpl);
			if (sim > bestSim) {
				bestSim = sim;
			}
		}
		Arrays.sort(lens);
		int medianLen = lens[lens.length / 2];
		double ratio = (double) candidate.length / (double) Math.max(1, medianLen);
		if (ratio < 0.50 || ratio > 1.95) {
			return "Duração muito diferente da 1ª gravação (use X para refazer)";
		}
		if (bestSim < 0.34) {
			int pct = (int) Math.round(bestSim * 100.0);
			return "Som muito diferente da gravação anterior (" + pct + "%) — tente de novo";
		}
		return null;
	}

	public synchronized VoiceBinding recordWordSample(String rawWord, String spellDisplayName,
													  String spellFingerprint, float[][] features, boolean forceReplace) {
		String key = normalizeWord(rawWord);
		VoiceBinding existing = wordTemplates.get(key);
		if (existing == null || forceReplace) {
			existing = new VoiceBinding(-1, key, spellDisplayName, spellFingerprint);
			wordTemplates.put(key, existing);
		} else {
			if (spellDisplayName != null && !spellDisplayName.isEmpty()) {
				existing.spellDisplayName = spellDisplayName;
			}
			if (spellFingerprint != null && !spellFingerprint.isEmpty()) {
				existing.spellFingerprint = spellFingerprint;
			}
		}
		if (existing.samples.size() >= MAX_SAMPLES_PER_WORD) {
			existing.samples.remove(0);
		}
		existing.samples.add(features);
		save();
		return existing;
	}

	public synchronized boolean removeBinding(String rawWord) {
		if (rawWord == null) return false;
		boolean removed = wordTemplates.remove(normalizeWord(rawWord)) != null;
		if (removed) {
			save();
		}
		return removed;
	}

	public synchronized int clearAll() {
		int n = wordTemplates.size();
		if (n > 0) {
			wordTemplates.clear();
			save();
		}
		return n;
	}

	/**
	 * Evaluates spoken query features against the active world's VaultCandidates (slot 0..7 + word).
	 * Falls back to standalone debug bindings only when vaultCandidates is empty/null.
	 */
	public synchronized MatchResult evaluateVault(float[][] queryFeatures, List<VaultCandidate> vaultCandidates,
												  double minThreshold, double minMargin) {
		if (queryFeatures == null || queryFeatures.length < MIN_TRIMMED_FRAMES || wordTemplates.isEmpty()) {
			return null;
		}

		List<VoiceBinding> effectiveCandidates = new ArrayList<VoiceBinding>();
		if (vaultCandidates != null && !vaultCandidates.isEmpty()) {
			for (VaultCandidate vc : vaultCandidates) {
				if (vc.word.isEmpty()) continue;
				VoiceBinding tpl = wordTemplates.get(vc.word);
				if (tpl != null && !tpl.samples.isEmpty()) {
					VoiceBinding resolved = new VoiceBinding(vc.slotIndex, vc.word, vc.spellLabel, "slot:" + vc.slotIndex);
					resolved.samples.addAll(tpl.samples);
					effectiveCandidates.add(resolved);
				}
			}
		} else {
			for (VoiceBinding b : wordTemplates.values()) {
				if (!b.samples.isEmpty() && b.spellFingerprint != null && !b.spellFingerprint.startsWith("slot:")) {
					effectiveCandidates.add(b);
				}
			}
		}

		if (effectiveCandidates.isEmpty()) {
			return null;
		}

		VoiceBinding bestBinding = null;
		VoiceBinding secondBinding = null;
		double bestScore = -1.0;
		double secondBest = -1.0;

		for (VoiceBinding candidate : effectiveCandidates) {
			double top1 = -1.0;
			double top2 = -1.0;
			double sum = 0.0;
			int n = candidate.samples.size();
			for (int i = 0; i < n; i++) {
				double s = scoreSymmetricDtw(queryFeatures, candidate.samples.get(i));
				sum += s;
				if (s > top1) {
					top2 = top1;
					top1 = s;
				} else if (s > top2) {
					top2 = s;
				}
			}
			double combined;
			if (n == 1) {
				combined = top1;
			} else if (n == 2) {
				// Consensus between both samples prevents a single bad recording from triggering false matches
				combined = 0.60 * top1 + 0.40 * top2;
			} else {
				combined = 0.55 * top1 + 0.30 * top2 + 0.15 * (sum / (double) n);
			}

			if (combined > bestScore) {
				secondBest = bestScore;
				secondBinding = bestBinding;
				bestScore = combined;
				bestBinding = candidate;
			} else if (combined > secondBest) {
				secondBest = combined;
				secondBinding = candidate;
			}
		}

		if (bestBinding == null) {
			return null;
		}

		boolean aboveThreshold = bestScore >= minThreshold;
		boolean sameSlotOrWord = secondBinding != null
				&& ((bestBinding.vaultSlot >= 0 && bestBinding.vaultSlot == secondBinding.vaultSlot)
				|| bestBinding.word.equals(secondBinding.word));
		boolean ambiguous = aboveThreshold
				&& effectiveCandidates.size() > 1
				&& !sameSlotOrWord
				&& secondBest >= 0.0
				&& (bestScore - secondBest) < minMargin;
		boolean accepted = aboveThreshold && !ambiguous;

		return new MatchResult(bestBinding, bestScore, secondBinding, secondBest, accepted, ambiguous);
	}

	/**
	 * Extracts 36-D phoneme-discriminative MFCC + Delta + Delta-Delta vectors from raw PCM frames.
	 */
	public static float[][] extractFeatures(List<short[]> rawFrames, int sampleRate) {
		if (rawFrames == null || rawFrames.isEmpty()) {
			return null;
		}
		int decimation = Math.max(1, Math.round((float) sampleRate / (float) TARGET_SAMPLE_RATE));
		int totalRaw = 0;
		for (short[] f : rawFrames) {
			totalRaw += f.length;
		}
		int downLen = totalRaw / decimation;
		if (downLen < FRAME_SAMPLES + FRAME_HOP * 6) {
			return null;
		}

		float[] mono = new float[downLen];
		int outPos = 0;
		int acc = 0;
		int accCount = 0;
		for (short[] f : rawFrames) {
			for (int i = 0; i < f.length; i++) {
				acc += f[i];
				accCount++;
				if (accCount == decimation && outPos < downLen) {
					mono[outPos++] = (acc / (float) decimation) / 32768.0f;
					acc = 0;
					accCount = 0;
				}
			}
		}
		if (outPos < FRAME_SAMPLES + FRAME_HOP * 6) {
			return null;
		}

		// Pre-emphasis filter: boosts consonants (t, r, v, c, b, s, f) for sharper phoneme separation
		for (int i = outPos - 1; i > 0; i--) {
			mono[i] = mono[i] - 0.97f * mono[i - 1];
		}

		int rawNumFrames = 1 + (outPos - FRAME_SAMPLES) / FRAME_HOP;
		if (rawNumFrames < MIN_TRIMMED_FRAMES) {
			return null;
		}

		float[][] mfccAll = new float[rawNumFrames][NUM_CEPSTRA];
		float[] frameLogEnergy = new float[rawNumFrames];
		double[] real = new double[FFT_SIZE];
		double[] imag = new double[FFT_SIZE];
		double[] power = new double[FFT_SIZE / 2 + 1];
		double[] logMel = new double[NUM_MEL_FILTERS];

		for (int f = 0; f < rawNumFrames; f++) {
			int start = f * FRAME_HOP;
			double totalPower = 1e-9;
			for (int i = 0; i < FFT_SIZE; i++) {
				real[i] = (i < FRAME_SAMPLES) ? (mono[start + i] * HAMMING_WINDOW[i]) : 0.0;
				imag[i] = 0.0;
			}
			fftRadix2(real, imag);
			for (int k = 1; k < power.length; k++) {
				double p = real[k] * real[k] + imag[k] * imag[k];
				power[k] = p;
				totalPower += p;
			}
			frameLogEnergy[f] = (float) Math.log(totalPower);

			for (int b = 0; b < NUM_MEL_FILTERS; b++) {
				int left = BAND_EDGES[b];
				int center = BAND_EDGES[b + 1];
				int right = BAND_EDGES[b + 2];
				double energy = 1e-9;
				for (int k = left; k <= right; k++) {
					double weight;
					if (k <= center) {
						weight = (center == left) ? 1.0 : (double) (k - left) / (double) (center - left);
					} else {
						weight = (right == center) ? 1.0 : (double) (right - k) / (double) (right - center);
					}
					if (weight > 0.0) {
						energy += power[k] * weight;
					}
				}
				logMel[b] = Math.log(energy);
			}

			// DCT-II keeping c1..c12 (c0 dropped!) with sinusoidal liftering
			for (int c = 0; c < NUM_CEPSTRA; c++) {
				double[] dctRow = DCT_LIFTERED[c];
				double sum = 0.0;
				for (int b = 0; b < NUM_MEL_FILTERS; b++) {
					sum += dctRow[b] * logMel[b];
				}
				mfccAll[f][c] = (float) sum;
			}
		}

		// Robust median-filtered contiguous-island endpoint trimming:
		// 5-frame median filter completely erases brief impulsive clicks/pops (10-25ms) without smearing edges!
		float[] smoothE = new float[rawNumFrames];
		float[] win5 = new float[5];
		for (int f = 0; f < rawNumFrames; f++) {
			for (int d = -2; d <= 2; d++) {
				int idx = Math.max(0, Math.min(rawNumFrames - 1, f + d));
				win5[d + 2] = frameLogEnergy[idx];
			}
			Arrays.sort(win5);
			smoothE[f] = win5[2];
		}

		float[] sortedE = Arrays.copyOf(smoothE, rawNumFrames);
		Arrays.sort(sortedE);
		float noiseFloor = sortedE[Math.max(0, (rawNumFrames * 15) / 100)];
		float peakE = sortedE[Math.min(rawNumFrames - 1, (rawNumFrames * 95) / 100)];
		float dynamicRange = peakE - noiseFloor;
		if (dynamicRange < 1.25f) {
			// Flat background noise with no actual speech energy rise
			return null;
		}
		float speechGate = Math.max(peakE - 3.25f, noiseFloor + 0.38f * dynamicRange);

		// Collect speech islands of at least 5 contiguous active frames (~50ms)
		List<int[]> islands = new ArrayList<int[]>();
		int runStart = -1;
		for (int f = 0; f <= rawNumFrames; f++) {
			boolean active = (f < rawNumFrames) && (smoothE[f] >= speechGate);
			if (active) {
				if (runStart < 0) {
					runStart = f;
				}
			} else if (runStart >= 0) {
				int runLen = f - runStart;
				if (runLen >= 5) {
					islands.add(new int[]{runStart, f - 1});
				}
				runStart = -1;
			}
		}
		if (islands.isEmpty()) {
			return null;
		}

		// Anchor on the largest island and merge nearby real syllables, dropping isolated distant blips
		int mainIdx = 0;
		int mainLen = 0;
		for (int i = 0; i < islands.size(); i++) {
			int len = islands.get(i)[1] - islands.get(i)[0] + 1;
			if (len > mainLen) {
				mainLen = len;
				mainIdx = i;
			}
		}
		int trimStart = islands.get(mainIdx)[0];
		int trimEnd = islands.get(mainIdx)[1];
		for (int i = mainIdx - 1; i >= 0; i--) {
			int[] isl = islands.get(i);
			int gap = trimStart - isl[1];
			int len = isl[1] - isl[0] + 1;
			if (gap <= 28 || len >= 10) {
				trimStart = isl[0];
			} else {
				break;
			}
		}
		for (int i = mainIdx + 1; i < islands.size(); i++) {
			int[] isl = islands.get(i);
			int gap = isl[0] - trimEnd;
			int len = isl[1] - isl[0] + 1;
			if (gap <= 28 || len >= 10) {
				trimEnd = isl[1];
			} else {
				break;
			}
		}

		// Small 2-frame transition padding so soft initial/final consonants aren't clipped
		trimStart = Math.max(0, trimStart - 2);
		trimEnd = Math.min(rawNumFrames - 1, trimEnd + 2);
		int numFrames = trimEnd - trimStart + 1;
		if (numFrames < MIN_TRIMMED_FRAMES || numFrames > MAX_TRIMMED_FRAMES) {
			return null;
		}

		float[][] trimmed = new float[numFrames][NUM_CEPSTRA];
		for (int f = 0; f < numFrames; f++) {
			System.arraycopy(mfccAll[trimStart + f], 0, trimmed[f], 0, NUM_CEPSTRA);
		}

		// Utterance-level CMVN (zero mean, unit variance per cepstral coefficient)
		for (int c = 0; c < NUM_CEPSTRA; c++) {
			double mean = 0.0;
			for (int f = 0; f < numFrames; f++) {
				mean += trimmed[f][c];
			}
			mean /= numFrames;
			double var = 0.0;
			for (int f = 0; f < numFrames; f++) {
				double d = trimmed[f][c] - mean;
				var += d * d;
			}
			double std = Math.sqrt(var / numFrames) + 1e-4;
			for (int f = 0; f < numFrames; f++) {
				trimmed[f][c] = (float) ((trimmed[f][c] - mean) / std);
			}
		}

		// First-order Delta (captures consonant-vowel transitions)
		float[][] delta = new float[numFrames][NUM_CEPSTRA];
		for (int f = 0; f < numFrames; f++) {
			int p1 = Math.max(0, f - 1);
			int p2 = Math.max(0, f - 2);
			int n1 = Math.min(numFrames - 1, f + 1);
			int n2 = Math.min(numFrames - 1, f + 2);
			for (int c = 0; c < NUM_CEPSTRA; c++) {
				delta[f][c] = ((trimmed[n1][c] - trimmed[p1][c]) + 2.0f * (trimmed[n2][c] - trimmed[p2][c])) / 5.0f;
			}
		}

		// Second-order Delta-Delta + unit L2 normalization
		float[][] features = new float[numFrames][FEATURE_DIM];
		for (int f = 0; f < numFrames; f++) {
			int p1 = Math.max(0, f - 1);
			int p2 = Math.max(0, f - 2);
			int n1 = Math.min(numFrames - 1, f + 1);
			int n2 = Math.min(numFrames - 1, f + 2);

			double normSq = 0.0;
			for (int c = 0; c < NUM_CEPSTRA; c++) {
				float stat = trimmed[f][c];
				float d1 = DELTA_WEIGHT * delta[f][c];
				float d2 = DELTA2_WEIGHT * (((delta[n1][c] - delta[p1][c]) + 2.0f * (delta[n2][c] - delta[p2][c])) / 5.0f);
				features[f][c] = stat;
				features[f][NUM_CEPSTRA + c] = d1;
				features[f][NUM_CEPSTRA * 2 + c] = d2;
				normSq += stat * stat + d1 * d1 + d2 * d2;
			}
			float inv = (float) (1.0 / (Math.sqrt(normSq) + 1e-6));
			for (int k = 0; k < FEATURE_DIM; k++) {
				features[f][k] *= inv;
			}
		}

		return features;
	}

	/**
	 * Discriminative Symmetric Sakoe-Chiba DTW with tighter warping band (16%)
	 * and gentle log-duration mismatch penalty.
	 */
	public static double scoreSymmetricDtw(float[][] a, float[][] b) {
		int n = a.length;
		int m = b.length;
		if (n == 0 || m == 0) return 0.0;

		double ratio = (double) n / (double) m;
		if (ratio < 0.50 || ratio > 2.00) {
			return 0.0;
		}

		int band = Math.max(6, Math.abs(n - m) + Math.max(n, m) / 6);
		double[] prev = new double[m + 1];
		double[] curr = new double[m + 1];
		Arrays.fill(prev, Double.POSITIVE_INFINITY);

		for (int i = 1; i <= n; i++) {
			Arrays.fill(curr, Double.POSITIVE_INFINITY);
			int centerJ = (int) Math.round((double) i * m / (double) n);
			int jStart = Math.max(1, centerJ - band);
			int jEnd = Math.min(m, centerJ + band);
			float[] ai = a[i - 1];

			for (int j = jStart; j <= jEnd; j++) {
				double d = cosineDistance(ai, b[j - 1]);
				if (i == 1 && j == 1) {
					curr[j] = 2.0 * d;
					continue;
				}
				double diag = (prev[j - 1] < Double.POSITIVE_INFINITY) ? (prev[j - 1] + 2.0 * d) : Double.POSITIVE_INFINITY;
				double vert = (prev[j] < Double.POSITIVE_INFINITY) ? (prev[j] + d) : Double.POSITIVE_INFINITY;
				double horiz = (curr[j - 1] < Double.POSITIVE_INFINITY) ? (curr[j - 1] + d) : Double.POSITIVE_INFINITY;
				double best = diag;
				if (vert < best) best = vert;
				if (horiz < best) best = horiz;
				curr[j] = best;
			}
			double[] tmp = prev;
			prev = curr;
			curr = tmp;
		}

		double totalDist = prev[m];
		if (!Double.isFinite(totalDist)) {
			return 0.0;
		}
		double meanCosineDist = totalDist / (double) (n + m);
		double lenPenalty = 0.14 * Math.abs(Math.log(ratio));
		double score = (1.0 - meanCosineDist) - lenPenalty;
		return Math.max(0.0, Math.min(1.0, score));
	}

	private static double cosineDistance(float[] u, float[] v) {
		int len = Math.min(u.length, v.length);
		double dot = 0.0;
		for (int i = 0; i < len; i++) {
			dot += u[i] * v[i];
		}
		if (dot > 1.0) dot = 1.0;
		if (dot < -1.0) dot = -1.0;
		return 1.0 - dot;
	}

	public static String computeSpellFingerprint(ItemStack spellStack) {
		if (spellStack == null || !(spellStack.getItem() instanceof ItemSpellBase)) {
			return "";
		}
		try {
			StringBuilder sb = new StringBuilder();
			int numGroups = SpellUtils.instance.numShapeGroups(spellStack);
			sb.append("G=").append(numGroups).append(';');
			for (int g = 0; g < numGroups; g++) {
				int[] parts = SpellUtils.instance.getShapeGroupParts(spellStack, g);
				sb.append('[');
				if (parts != null) {
					for (int p = 0; p < parts.length; p++) {
						if (p > 0) sb.append(',');
						sb.append(parts[p]);
					}
				}
				sb.append(']');
			}

			ItemStack parsed = SpellUtils.instance.constructSpellStack(spellStack.copy());
			int stages = SpellUtils.instance.numStages(parsed);
			sb.append("|S=").append(stages);
			for (int s = 0; s < stages; s++) {
				ISpellShape shape = SpellUtils.instance.getShapeForStage(parsed, s);
				sb.append('|').append(shape != null ? shape.getClass().getSimpleName() : "none").append(':');
				ISpellComponent[] comps = SpellUtils.instance.getComponentsForStage(parsed, s);
				if (comps != null) {
					for (int c = 0; c < comps.length; c++) {
						if (c > 0) sb.append('+');
						sb.append(comps[c] != null ? comps[c].getClass().getSimpleName() : "null");
					}
				}
				ISpellModifier[] mods = SpellUtils.instance.getModifiersForStage(parsed, s);
				if (mods != null && mods.length > 0) {
					sb.append('#');
					for (int m = 0; m < mods.length; m++) {
						if (m > 0) sb.append(',');
						sb.append(mods[m] != null ? mods[m].getClass().getSimpleName() : "null");
					}
				}
			}
			return Integer.toHexString(sb.toString().hashCode()) + ":" + stages + ":" + numGroups;
		} catch (Throwable t) {
			return spellStack.getDisplayName();
		}
	}

	public static String describeSpell(ItemStack spellStack) {
		return describeSpellInternal(spellStack, false);
	}

	/**
	 * Compact human-friendly spell label for tight UI cards:
	 * omits redundant "Unnamed Spell [Shape:" boilerplate when the scroll has not been custom-named.
	 */
	public static String describeSpellCompact(ItemStack spellStack) {
		return describeSpellInternal(spellStack, true);
	}

	private static String describeSpellInternal(ItemStack spellStack, boolean compact) {
		if (spellStack == null) return "Unknown Spell";
		String rawName = EnumChatFormatting.getTextWithoutFormattingCodes(spellStack.getDisplayName());
		if (rawName == null || rawName.trim().isEmpty()) {
			rawName = "Unnamed Spell";
		}
		boolean isGeneric = rawName.toLowerCase(Locale.ROOT).contains("unnamed spell")
				|| rawName.toLowerCase(Locale.ROOT).endsWith(" spell");
		if (!isGeneric) {
			return rawName.trim();
		}
		try {
			ItemStack parsed = SpellUtils.instance.constructSpellStack(spellStack.copy());
			ISpellShape shape = SpellUtils.instance.getShapeForStage(parsed, 0);
			ISpellComponent[] comps = SpellUtils.instance.getComponentsForStage(parsed, 0);
			StringBuilder desc = new StringBuilder();
			if (!compact && shape != null) {
				desc.append(prettyPartName(shape.getClass().getSimpleName()));
				if (comps != null && comps.length > 0) {
					desc.append(": ");
				}
			}
			if (comps != null && comps.length > 0) {
				int limit = Math.min(compact ? 2 : 3, comps.length);
				for (int i = 0; i < limit; i++) {
					if (i > 0) desc.append(" + ");
					desc.append(prettyPartName(comps[i].getClass().getSimpleName()));
				}
				if (comps.length > limit) desc.append("..");
			} else if (shape != null) {
				desc.append(prettyPartName(shape.getClass().getSimpleName()));
			}
			String out = desc.toString().trim();
			return out.isEmpty() ? rawName.trim() : out;
		} catch (Throwable t) {
			return rawName.trim();
		}
	}

	private static String prettyPartName(String simpleName) {
		if (simpleName == null || simpleName.isEmpty()) return "";
		String s = simpleName;
		if (s.endsWith("Damage") && s.length() > 6) {
			s = s.substring(0, s.length() - 6);
		} else if (s.startsWith("Create") && s.length() > 6) {
			s = s.substring(6);
		} else if (s.startsWith("Summon") && s.length() > 6) {
			s = "Summon " + s.substring(6);
		}
		// Insert space before internal capital letters (e.g. FrostNova -> Frost Nova)
		StringBuilder sb = new StringBuilder(s.length() + 4);
		for (int i = 0; i < s.length(); i++) {
			char ch = s.charAt(i);
			if (i > 0 && Character.isUpperCase(ch) && Character.isLowerCase(s.charAt(i - 1))) {
				sb.append(' ');
			}
			sb.append(ch);
		}
		return sb.toString();
	}

	public static boolean isGenericUnnamedLabel(String label) {
		if (label == null) return true;
		String lower = EnumChatFormatting.getTextWithoutFormattingCodes(label).toLowerCase(Locale.ROOT);
		return lower.contains("unnamed spell");
	}

	private static void fftRadix2(double[] real, double[] imag) {
		int n = real.length;
		for (int i = 1, j = 0; i < n; i++) {
			int bit = n >> 1;
			for (; (j & bit) != 0; bit >>= 1) {
				j ^= bit;
			}
			j ^= bit;
			if (i < j) {
				double tr = real[i];
				real[i] = real[j];
				real[j] = tr;
				double ti = imag[i];
				imag[i] = imag[j];
				imag[j] = ti;
			}
		}
		for (int len = 2; len <= n; len <<= 1) {
			double ang = -2.0 * Math.PI / len;
			double wlenR = Math.cos(ang);
			double wlenI = Math.sin(ang);
			int half = len >> 1;
			for (int i = 0; i < n; i += len) {
				double wr = 1.0;
				double wi = 0.0;
				for (int j = 0; j < half; j++) {
					int u = i + j;
					int v = u + half;
					double vr = real[v] * wr - imag[v] * wi;
					double vi = real[v] * wi + imag[v] * wr;
					real[v] = real[u] - vr;
					imag[v] = imag[u] - vi;
					real[u] += vr;
					imag[u] += vi;
					double nextWr = wr * wlenR - wi * wlenI;
					wi = wr * wlenI + wi * wlenR;
					wr = nextWr;
				}
			}
		}
	}

	private static float[] buildHammingWindow(int size) {
		float[] w = new float[size];
		for (int i = 0; i < size; i++) {
			w[i] = (float) (0.54 - 0.46 * Math.cos(2.0 * Math.PI * i / (size - 1)));
		}
		return w;
	}

	private static double[][] buildDctLifteredMatrix(int numCepstra, int numMelFilters, double lifterL) {
		double[][] mat = new double[numCepstra][numMelFilters];
		double scale = Math.sqrt(2.0 / numMelFilters);
		for (int c = 0; c < numCepstra; c++) {
			int k = c + 1; // drop c0!
			double lifter = 1.0 + 0.5 * lifterL * Math.sin(Math.PI * k / lifterL);
			for (int b = 0; b < numMelFilters; b++) {
				mat[c][b] = lifter * scale * Math.cos(Math.PI * k * (b + 0.5) / numMelFilters);
			}
		}
		return mat;
	}

	private static int[] buildMelBandEdges(int numBands, int sampleRate, int fftSize, double lowHz, double highHz) {
		double lowMel = hzToMel(lowHz);
		double highMel = hzToMel(highHz);
		int[] bins = new int[numBands + 2];
		int maxBin = fftSize / 2;
		for (int i = 0; i < bins.length; i++) {
			double mel = lowMel + (highMel - lowMel) * i / (numBands + 1);
			double hz = melToHz(mel);
			int bin = (int) Math.floor((fftSize + 1) * hz / sampleRate);
			if (bin < 1) bin = 1;
			if (bin > maxBin) bin = maxBin;
			bins[i] = bin;
		}
		return bins;
	}

	private static double hzToMel(double hz) {
		return 2595.0 * Math.log10(1.0 + hz / 700.0);
	}

	private static double melToHz(double mel) {
		return 700.0 * (Math.pow(10.0, mel / 2595.0) - 1.0);
	}

	public static String normalizeWord(String word) {
		if (word == null) return "";
		return word.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
	}

	private synchronized void save() {
		if (storageFile == null) return;
		File parent = storageFile.getParentFile();
		if (parent != null && !parent.exists()) {
			parent.mkdirs();
		}
		DataOutputStream out = null;
		try {
			out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(storageFile)));
			out.writeInt(FILE_MAGIC);
			out.writeInt(FILE_VERSION);
			out.writeBoolean(prefContinuousListen);
			out.writeDouble(prefMatchThreshold);
			out.writeDouble(prefVadThreshold);
			out.writeInt(wordTemplates.size());
			for (VoiceBinding b : wordTemplates.values()) {
				out.writeUTF(b.word != null ? b.word : "");
				out.writeUTF(b.spellDisplayName != null ? b.spellDisplayName : "");
				out.writeUTF(b.spellFingerprint != null ? b.spellFingerprint : "");
				out.writeInt(b.samples.size());
				for (float[][] sample : b.samples) {
					out.writeInt(sample.length);
					out.writeInt(FEATURE_DIM);
					for (int f = 0; f < sample.length; f++) {
						for (int k = 0; k < FEATURE_DIM; k++) {
							out.writeFloat(sample[f][k]);
						}
					}
				}
			}
			out.flush();
		} catch (IOException e) {
			LogHelper.warn("Failed to save AM2 voice bindings: %s", e.getMessage());
		} finally {
			if (out != null) {
				try {
					out.close();
				} catch (IOException ignored) {
				}
			}
		}
	}

	private synchronized void load() {
		if (storageFile == null || !storageFile.isFile()) return;
		DataInputStream in = null;
		boolean needsResave = false;
		try {
			in = new DataInputStream(new BufferedInputStream(new FileInputStream(storageFile)));
			int magic = in.readInt();
			int version = in.readInt();
			if ((magic == 0x414D5634 && version == 4) || (magic == 0x414D5633 && version == 3)) {
				// v3/v4 stored raw Log-Mel 32-D vectors which are incompatible with v5 MFCC 36-D vectors.
				// Preserve mode & VAD preferences, reset matchThreshold to optimal MFCC default (0.52),
				// and ask the player once to re-record their vocal samples for vastly better accuracy.
				if (version == 4) {
					prefContinuousListen = in.readBoolean();
					in.readDouble(); // discard old v4 Log-Mel threshold (e.g. 0.73)
					prefVadThreshold = Math.max(0.003, Math.min(0.30, in.readDouble()));
				}
				prefMatchThreshold = DEFAULT_MATCH_THRESHOLD;
				migratedFromLegacyFormat = true;
				needsResave = true;
				return;
			}
			if (magic != FILE_MAGIC || version != FILE_VERSION) {
				return;
			}
			prefContinuousListen = in.readBoolean();
			prefMatchThreshold = Math.max(0.35, Math.min(0.90, in.readDouble()));
			prefVadThreshold = Math.max(0.003, Math.min(0.30, in.readDouble()));
			int count = in.readInt();
			if (count < 0 || count > 256) return;
			for (int i = 0; i < count; i++) {
				String word = normalizeWord(in.readUTF());
				String spellName = in.readUTF();
				String fingerprint = in.readUTF();
				int sampleCount = in.readInt();
				if (sampleCount < 0 || sampleCount > 8) break;
				VoiceBinding b = new VoiceBinding(-1, word, spellName, fingerprint);
				for (int s = 0; s < sampleCount; s++) {
					int frames = in.readInt();
					int dim = in.readInt();
					if (frames <= 0 || frames > 600 || dim != FEATURE_DIM) break;
					float[][] sample = new float[frames][dim];
					for (int f = 0; f < frames; f++) {
						for (int k = 0; k < dim; k++) {
							sample[f][k] = in.readFloat();
						}
					}
					b.samples.add(sample);
				}
				if (!word.isEmpty() && !b.samples.isEmpty()) {
					wordTemplates.put(word, b);
				}
			}
		} catch (IOException e) {
			LogHelper.warn("Failed to load AM2 voice bindings: %s", e.getMessage());
		} finally {
			if (in != null) {
				try {
					in.close();
				} catch (IOException ignored) {
				}
			}
		}
		if (needsResave) {
			save();
		}
	}
}
