package am2.interop.voice;

import am2.AMCore;
import am2.AMKeyBindings;
import am2.LogHelper;
import am2.api.spell.ItemSpellBase;
import am2.items.ItemSpellBook;
import am2.network.AMDataReader;
import am2.network.AMNetHandler;
import am2.particles.AMParticle;
import am2.particles.ParticleFloatUpward;
import am2.playerextensions.VoiceVaultStorage;
import am2.skills.VocalResonance;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

/**
 * Client-side voice incantation engine v4:
 * - Word-indexed templates (safe across multiple worlds & servers)
 * - Persistent preferences (mode, match threshold, VAD)
 * - Smart Continuous-Listen anti-false-positive threshold guard (+8%)
 * - Safe in-GUI Voice Test mode (highlights matched slot card without firing spells)
 * - Client-side harmonic particle ring when any nearby player casts by voice
 */
@SideOnly(Side.CLIENT)
public final class VoiceSpellController {

	private static final int MIN_RECORD_FRAMES = 14;
	private static final int MIN_RECORD_VOICED_FRAMES = 7;
	private static final int MIN_LISTEN_FRAMES = 10;
	private static final int MIN_LISTEN_VOICED_FRAMES = 5;
	private static final int MAX_UTTERANCE_FRAMES = 125;
	private static final int CONTINUOUS_MAX_CHATTER_FRAMES = 105;
	private static final int RECORD_END_SILENCE_FRAMES = 22;
	private static final int LISTEN_END_SILENCE_FRAMES = 16;
	private static final long BIND_ARM_DELAY_MS = 450L;
	private static final long GUI_ARM_DELAY_MS = 280L;
	private static final long RECORD_TIMEOUT_MS = 8000L;
	private static final long CAST_COOLDOWN_MS = 550L;

	private static final class RecordingRequest {
		final int vaultSlot;
		final String word;
		final String spellLabel;
		final String spellFingerprint;
		final boolean forceReplace;
		final long armAtMillis;
		final long expiresAtMillis;

		RecordingRequest(int vaultSlot, String word, String spellLabel, String spellFingerprint,
						 boolean forceReplace, long armAtMillis, long expiresAtMillis) {
			this.vaultSlot = vaultSlot;
			this.word = word;
			this.spellLabel = spellLabel;
			this.spellFingerprint = spellFingerprint;
			this.forceReplace = forceReplace;
			this.armAtMillis = armAtMillis;
			this.expiresAtMillis = expiresAtMillis;
		}
	}

	private static final class PendingCast {
		final VoiceAcousticMatcher.VoiceBinding binding;
		final double score;

		PendingCast(VoiceAcousticMatcher.VoiceBinding binding, double score) {
			this.binding = binding;
			this.score = score;
		}
	}

	private static volatile VoiceSpellController instance;

	private final VoiceAcousticMatcher matcher;
	private final ConcurrentLinkedQueue<PendingCast> pendingCasts = new ConcurrentLinkedQueue<PendingCast>();
	private final ConcurrentLinkedQueue<String> pendingChatMessages = new ConcurrentLinkedQueue<String>();

	private volatile boolean continuousListen;
	private volatile boolean keyHeld = false;
	private volatile boolean guiTestMode = false;
	private volatile double vadThreshold;
	private volatile double matchThreshold;
	private volatile double matchMargin = 0.05;
	private volatile RecordingRequest activeRecording = null;

	private volatile int lastTestedSlot = -1;
	private volatile double lastTestedScore = 0.0;
	private volatile long lastTestedUntilMillis = 0L;

	private volatile String hudBannerText = "";
	private volatile long hudBannerUntilMillis = 0L;
	private volatile long lastCastMillis = 0L;

	private VoiceSpellController(File configFile) {
		this.matcher = new VoiceAcousticMatcher(configFile);
		this.continuousListen = this.matcher.getPrefContinuousListen();
		this.matchThreshold = this.matcher.getPrefMatchThreshold();
		this.vadThreshold = this.matcher.getPrefVadThreshold();
	}

	public static void initializeClient() {
		if (instance != null) return;
		Minecraft mc = Minecraft.getMinecraft();
		File configDir = (mc != null && mc.mcDataDir != null) ? new File(mc.mcDataDir, "config/AM2") : new File("config/AM2");
		VoiceSpellController controller = new VoiceSpellController(new File(configDir, "voice_bindings.dat"));
		instance = controller;

		AkashicVoiceBridge.initialize();

		FMLCommonHandler.instance().bus().register(controller);
		MinecraftForge.EVENT_BUS.register(controller);

		Thread worker = new Thread(new Runnable() {
			@Override
			public void run() {
				controller.workerLoop();
			}
		}, "AM2-VoiceSpell-Worker");
		worker.setDaemon(true);
		worker.start();
	}

	public static VoiceSpellController get() {
		return instance;
	}

	public static boolean shouldAcceptFrames(long nowMillis) {
		VoiceSpellController c = instance;
		if (c == null) return false;
		RecordingRequest rec = c.activeRecording;
		if (rec != null) {
			return nowMillis >= rec.armAtMillis;
		}
		if (c.guiTestMode) {
			return c.matcher.bindingCount() > 0 && !AkashicVoiceBridge.isMuted();
		}
		if (!c.keyHeld && !c.continuousListen) return false;
		Minecraft mc = Minecraft.getMinecraft();
		if (mc == null || mc.thePlayer == null || mc.currentScreen != null || !VocalResonance.isUnlocked(mc.thePlayer)) {
			return false;
		}
		return c.matcher.bindingCount() > 0 && !AkashicVoiceBridge.isMuted() && !AkashicVoiceBridge.isLocalPlayerSilenced();
	}

	public VoiceAcousticMatcher getMatcher() {
		return matcher;
	}

	public boolean isContinuousListen() {
		return continuousListen;
	}

	public void setContinuousListen(boolean enabled) {
		this.continuousListen = enabled;
		this.matcher.savePreferences(this.continuousListen, this.matchThreshold, this.vadThreshold);
	}

	public boolean isGuiTestMode() {
		return guiTestMode;
	}

	public void setGuiTestMode(boolean enabled) {
		this.guiTestMode = enabled;
		if (enabled) {
			this.activeRecording = null;
			AkashicVoiceBridge.clearBuffers();
			this.hudBannerText = EnumChatFormatting.GREEN + "Modo Teste ativo: fale qualquer palavra gravada!";
			this.hudBannerUntilMillis = System.currentTimeMillis() + 3000L;
		}
	}

	public int getHighlightedTestSlot() {
		return System.currentTimeMillis() < lastTestedUntilMillis ? lastTestedSlot : -1;
	}

	public double getHighlightedTestScore() {
		return lastTestedScore;
	}

	public double getVadThreshold() {
		return vadThreshold;
	}

	public double getEffectiveVadThreshold() {
		double adaptive = AkashicVoiceBridge.getNoiseFloorRms() * 2.35;
		if (adaptive > 0.075) adaptive = 0.075;
		return Math.max(vadThreshold, adaptive);
	}

	public void setVadThreshold(double value) {
		this.vadThreshold = Math.max(0.003, Math.min(0.30, value));
		this.matcher.savePreferences(this.continuousListen, this.matchThreshold, this.vadThreshold);
	}

	public double getMatchThreshold() {
		return matchThreshold;
	}

	public void setMatchThreshold(double value) {
		this.matchThreshold = Math.max(0.35, Math.min(0.90, value));
		this.matcher.savePreferences(this.continuousListen, this.matchThreshold, this.vadThreshold);
	}

	public boolean isRecording() {
		return activeRecording != null;
	}

	public int getRecordingVaultSlot() {
		RecordingRequest rec = activeRecording;
		return rec != null ? rec.vaultSlot : -1;
	}

	public boolean isRecordingArmed() {
		RecordingRequest rec = activeRecording;
		return rec != null && System.currentTimeMillis() >= rec.armAtMillis;
	}

	public String getActiveBannerText() {
		return System.currentTimeMillis() < hudBannerUntilMillis ? hudBannerText : "";
	}

	public void cancelRecording() {
		activeRecording = null;
	}

	public boolean startVaultSlotRecording(int slot, String word, ItemStack spellStack, boolean forceReplace) {
		if (!AkashicVoiceBridge.isConnected()) {
			hudBannerText = EnumChatFormatting.RED + AkashicVoiceBridge.getStatusReason();
			hudBannerUntilMillis = System.currentTimeMillis() + 3000L;
			return false;
		}
		String normalized = VoiceAcousticMatcher.normalizeWord(word);
		if (normalized.isEmpty()) {
			hudBannerText = EnumChatFormatting.YELLOW + "Digite uma palavra antes de gravar!";
			hudBannerUntilMillis = System.currentTimeMillis() + 2500L;
			return false;
		}
		this.guiTestMode = false;
		long now = System.currentTimeMillis();
		AkashicVoiceBridge.clearBuffers();
		String spellLabel = spellStack != null ? VoiceAcousticMatcher.describeSpell(spellStack) : ("Slot #" + (slot + 1));
		activeRecording = new RecordingRequest(
				slot, normalized, spellLabel, "slot:" + slot, forceReplace,
				now + GUI_ARM_DELAY_MS, now + GUI_ARM_DELAY_MS + RECORD_TIMEOUT_MS);
		hudBannerText = EnumChatFormatting.YELLOW + "Fale '" + normalized + "' para o Slot #" + (slot + 1) + "...";
		hudBannerUntilMillis = now + 2500L;
		return true;
	}

	public boolean startRecording(String word, ItemStack spellStack, boolean forceReplace) {
		Minecraft mc = Minecraft.getMinecraft();
		if (mc == null || mc.thePlayer == null || !VocalResonance.isUnlocked(mc.thePlayer)) {
			sendClientChat(EnumChatFormatting.RED + "[AM2 Voice] " + EnumChatFormatting.GRAY + "Requer o talento Ressonância Vocal (Nível 90+).");
			return false;
		}
		if (!AkashicVoiceBridge.isConnected()) {
			sendClientChat(EnumChatFormatting.RED + "[AM2 Voice] " + EnumChatFormatting.GRAY + AkashicVoiceBridge.getStatusReason());
			return false;
		}
		if (spellStack == null || !(spellStack.getItem() instanceof ItemSpellBase)) {
			sendClientChat(EnumChatFormatting.RED + "[AM2 Voice] " + EnumChatFormatting.GRAY + "Segure uma magia ou use a Aura Vocal.");
			return false;
		}
		String normalized = VoiceAcousticMatcher.normalizeWord(word);
		if (normalized.isEmpty()) return false;

		long now = System.currentTimeMillis();
		AkashicVoiceBridge.clearBuffers();
		String spellLabel = VoiceAcousticMatcher.describeSpell(spellStack);
		String fingerprint = VoiceAcousticMatcher.computeSpellFingerprint(spellStack);

		activeRecording = new RecordingRequest(
				-1, normalized, spellLabel, fingerprint, forceReplace,
				now + BIND_ARM_DELAY_MS, now + BIND_ARM_DELAY_MS + RECORD_TIMEOUT_MS);

		sendClientChat(EnumChatFormatting.AQUA + "[AM2 Voice] " + EnumChatFormatting.YELLOW
				+ "Prepare-se e fale '" + normalized + "' para vincular a "
				+ EnumChatFormatting.WHITE + spellLabel + EnumChatFormatting.YELLOW + "...");
		return true;
	}

	public boolean triggerTestCast(String rawWord) {
		Minecraft mc = Minecraft.getMinecraft();
		if (mc != null && mc.thePlayer != null) {
			VoiceVaultStorage vault = VoiceVaultStorage.For(mc.thePlayer);
			if (vault != null) {
				int slot = vault.findSlotByWord(rawWord);
				if (slot >= 0) {
					ItemStack s = vault.getStackInSlot(slot);
					String label = s != null ? VoiceAcousticMatcher.describeSpell(s) : ("Slot #" + (slot + 1));
					pendingCasts.offer(new PendingCast(new VoiceAcousticMatcher.VoiceBinding(slot, VoiceAcousticMatcher.normalizeWord(rawWord), label, "slot:" + slot), 1.0));
					return true;
				}
			}
		}
		VoiceAcousticMatcher.VoiceBinding binding = matcher.getBinding(rawWord);
		if (binding == null) {
			sendClientChat(EnumChatFormatting.RED + "[AM2 Voice] " + EnumChatFormatting.GRAY + "Palavra '" + rawWord + "' não encontrada.");
			return false;
		}
		pendingCasts.offer(new PendingCast(binding, 1.0));
		return true;
	}

	private void workerLoop() {
		List<short[]> utterance = new ArrayList<short[]>(96);
		List<short[]> localPreRoll = new ArrayList<short[]>(8);
		int sampleRate = 48000;
		boolean capturing = false;
		int silenceRun = 0;
		int voicedCount = 0;
		double peakRms = 0.0;
		RecordingRequest armedRecording = null;

		while (true) {
			try {
				AkashicVoiceBridge.CapturedFrame frame = AkashicVoiceBridge.getFrameQueue().poll(60L, TimeUnit.MILLISECONDS);
				long now = System.currentTimeMillis();

				RecordingRequest rec = activeRecording;
				if (rec != null && rec != armedRecording && now >= rec.armAtMillis) {
					armedRecording = rec;
					capturing = false;
					utterance.clear();
					localPreRoll.clear();
					silenceRun = 0;
					voicedCount = 0;
					peakRms = 0.0;
				}
				if (rec == null) {
					armedRecording = null;
				}

				if (rec != null && now > rec.expiresAtMillis) {
					if (capturing && utterance.size() >= MIN_RECORD_FRAMES && voicedCount >= MIN_RECORD_VOICED_FRAMES) {
						finishUtterance(utterance, voicedCount, peakRms, sampleRate, rec);
					} else {
						activeRecording = null;
						hudBannerText = EnumChatFormatting.RED + "Tempo esgotado gravando '" + rec.word + "'";
						hudBannerUntilMillis = now + 2600L;
						if (rec.vaultSlot < 0) {
							pendingChatMessages.offer(EnumChatFormatting.RED + "[AM2 Voice] " + EnumChatFormatting.GRAY
									+ "Tempo esgotado gravando '" + rec.word + "'.");
						}
					}
					capturing = false;
					utterance.clear();
					localPreRoll.clear();
					silenceRun = 0;
					voicedCount = 0;
					peakRms = 0.0;
					continue;
				}

				if (frame == null) {
					if (capturing) {
						if (!shouldAcceptFrames(now)) {
							if (utterance.size() >= MIN_LISTEN_FRAMES && voicedCount >= MIN_LISTEN_VOICED_FRAMES) {
								finishUtterance(utterance, voicedCount, peakRms, sampleRate, rec);
							}
							capturing = false;
							utterance.clear();
							localPreRoll.clear();
							silenceRun = 0;
							voicedCount = 0;
							peakRms = 0.0;
						} else if (utterance.size() >= MIN_LISTEN_FRAMES && silenceRun >= 6) {
							finishUtterance(utterance, voicedCount, peakRms, sampleRate, rec);
							capturing = false;
							utterance.clear();
							localPreRoll.clear();
							silenceRun = 0;
							voicedCount = 0;
							peakRms = 0.0;
						}
					}
					continue;
				}

				if (rec != null && frame.timestampMillis < rec.armAtMillis) {
					continue;
				}

				sampleRate = frame.sampleRate;
				double effVad = getEffectiveVadThreshold();
				boolean voiced = frame.rms >= effVad;

				if (!capturing) {
					if (voiced) {
						capturing = true;
						silenceRun = 0;
						voicedCount = 1;
						peakRms = frame.rms;
						utterance.clear();

						long minPreRollTime = (rec != null) ? rec.armAtMillis : (now - 300L);
						List<AkashicVoiceBridge.CapturedFrame> bridgePre = AkashicVoiceBridge.drainPreRoll(minPreRollTime);
						for (AkashicVoiceBridge.CapturedFrame pf : bridgePre) {
							utterance.add(pf.pcm);
						}
						for (short[] lp : localPreRoll) {
							utterance.add(lp);
						}
						localPreRoll.clear();
						utterance.add(frame.pcm);
					} else {
						if (localPreRoll.size() >= 6) {
							localPreRoll.remove(0);
						}
						localPreRoll.add(frame.pcm);
					}
				} else {
					utterance.add(frame.pcm);
					if (frame.rms > peakRms) {
						peakRms = frame.rms;
					}
					if (voiced) {
						voicedCount++;
						silenceRun = 0;
					} else {
						silenceRun++;
					}

					int endSilenceNeeded = (rec != null) ? RECORD_END_SILENCE_FRAMES : LISTEN_END_SILENCE_FRAMES;
					if (silenceRun >= endSilenceNeeded || utterance.size() >= MAX_UTTERANCE_FRAMES) {
						int keepTail = Math.min(3, silenceRun);
						int trimCount = Math.max(0, silenceRun - keepTail);
						int endIdx = Math.max(1, utterance.size() - trimCount);
						List<short[]> trimmed = new ArrayList<short[]>(utterance.subList(0, endIdx));

						boolean accepted = finishUtterance(trimmed, voicedCount, peakRms, sampleRate, rec);
						capturing = false;
						utterance.clear();
						localPreRoll.clear();
						silenceRun = 0;
						voicedCount = 0;
						peakRms = 0.0;
						if (!accepted && rec != null && System.currentTimeMillis() >= hudBannerUntilMillis) {
							hudBannerText = EnumChatFormatting.YELLOW + "Ruído curto ignorado — fale '" + rec.word + "'";
							hudBannerUntilMillis = System.currentTimeMillis() + 1800L;
						}
					}
				}
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return;
			} catch (Throwable t) {
				LogHelper.warn("AM2 Voice worker error: %s", t.toString());
				capturing = false;
				utterance.clear();
				localPreRoll.clear();
				silenceRun = 0;
				voicedCount = 0;
			}
		}
	}

	private boolean finishUtterance(List<short[]> frames, int voicedCount, double peakRms,
									int sampleRate, RecordingRequest recording) {
		double effVad = getEffectiveVadThreshold();
		if (recording != null) {
			if (frames.size() < MIN_RECORD_FRAMES || voicedCount < MIN_RECORD_VOICED_FRAMES || peakRms < effVad * 1.35) {
				return false;
			}
		} else {
			if (frames.size() < MIN_LISTEN_FRAMES || voicedCount < MIN_LISTEN_VOICED_FRAMES) {
				return false;
			}
			// In Continuous-Listen mode (not holding V and not in GUI Test), ignore long multi-second chatter sentences!
			if (!keyHeld && !guiTestMode && continuousListen && frames.size() > CONTINUOUS_MAX_CHATTER_FRAMES) {
				return true;
			}
		}

		float[][] features = VoiceAcousticMatcher.extractFeatures(frames, sampleRate);
		if (features == null) {
			if (recording != null) {
				hudBannerText = EnumChatFormatting.YELLOW + "Áudio muito curto ou longo — fale apenas '" + recording.word + "'";
				hudBannerUntilMillis = System.currentTimeMillis() + 2400L;
			}
			return false;
		}

		if (recording != null) {
			String rejection = matcher.validateSampleForWord(recording.word, features, recording.forceReplace);
			if (rejection != null) {
				hudBannerText = EnumChatFormatting.YELLOW + rejection;
				hudBannerUntilMillis = System.currentTimeMillis() + 2800L;
				return false;
			}
			activeRecording = null;
			VoiceAcousticMatcher.VoiceBinding saved = matcher.recordWordSample(
					recording.word, recording.spellLabel, recording.spellFingerprint, features, recording.forceReplace);
			int approxMs = features.length * 10 + 15;
			String hint = saved.samples.size() == 1 ? " (+1x para precisão ideal!)" : "";
			hudBannerText = EnumChatFormatting.GREEN + "Gravado: '" + saved.word + "' (" + saved.samples.size() + "/4, ~" + approxMs + "ms)" + hint;
			hudBannerUntilMillis = System.currentTimeMillis() + 3400L;
			if (recording.vaultSlot < 0) {
				pendingChatMessages.offer(EnumChatFormatting.GREEN + "[AM2 Voice] " + EnumChatFormatting.WHITE
						+ "Palavra '" + saved.word + "' gravada (" + saved.samples.size() + "/4 amostras).");
			}
			return true;
		}

		long now = System.currentTimeMillis();
		boolean inTest = this.guiTestMode;
		// Smart Continuous-Listen guard: +6% stricter threshold & wider margin when hands-free chatting
		double effThreshold = (!inTest && !keyHeld && continuousListen)
				? Math.min(0.86, matchThreshold + 0.06)
				: matchThreshold;
		double effMargin = (!inTest && !keyHeld && continuousListen) ? 0.065 : matchMargin;

		List<VoiceAcousticMatcher.VaultCandidate> candidates = buildVaultCandidates(inTest);
		VoiceAcousticMatcher.MatchResult eval = matcher.evaluateVault(features, candidates, effThreshold, effMargin);
		if (eval == null) {
			return true;
		}

		if (inTest) {
			int pct = (int) Math.round(eval.score * 100.0);
			int need = (int) Math.round(effThreshold * 100.0);
			String secondInfo = "";
			if (eval.secondBestBinding != null && eval.secondBestScore >= 0.0) {
				int p2 = (int) Math.round(eval.secondBestScore * 100.0);
				secondInfo = EnumChatFormatting.GRAY + " | 2º '" + eval.secondBestBinding.word + "' " + p2 + "%";
			}
			if (eval.accepted) {
				lastTestedSlot = eval.binding.vaultSlot;
				lastTestedScore = eval.score;
				lastTestedUntilMillis = now + 3000L;
				hudBannerText = EnumChatFormatting.GREEN + "OK #" + (eval.binding.vaultSlot + 1)
						+ " '" + eval.binding.word + "' " + pct + "%" + secondInfo;
			} else if (eval.ambiguous && eval.secondBestBinding != null) {
				int p2 = (int) Math.round(eval.secondBestScore * 100.0);
				hudBannerText = EnumChatFormatting.YELLOW + "Ambíguo: '" + eval.binding.word + "' " + pct + "% vs '"
						+ eval.secondBestBinding.word + "' " + p2 + "%";
			} else {
				hudBannerText = EnumChatFormatting.YELLOW + "Quase: #" + (eval.binding.vaultSlot + 1)
						+ " '" + eval.binding.word + "' " + pct + "% < " + need + "%" + secondInfo;
			}
			hudBannerUntilMillis = now + 3400L;
			return true;
		}

		if (now - lastCastMillis < CAST_COOLDOWN_MS) {
			return true;
		}

		if (eval.accepted) {
			lastCastMillis = now;
			pendingCasts.offer(new PendingCast(eval.binding, eval.score));
		} else if (eval.ambiguous && eval.secondBestBinding != null) {
			int p1 = (int) Math.round(eval.score * 100.0);
			int p2 = (int) Math.round(eval.secondBestScore * 100.0);
			hudBannerText = EnumChatFormatting.YELLOW + "Ambíguo: '" + eval.binding.word + "' (" + p1 + "%) vs '"
					+ eval.secondBestBinding.word + "' (" + p2 + "%)";
			hudBannerUntilMillis = now + 2200L;
		} else if (keyHeld && eval.score >= 0.42) {
			int pct = (int) Math.round(eval.score * 100.0);
			int need = (int) Math.round(effThreshold * 100.0);
			hudBannerText = EnumChatFormatting.YELLOW + "Quase: '" + eval.binding.word + "' (" + pct + "% < " + need + "%)";
			hudBannerUntilMillis = now + 2000L;
		}
		return true;
	}

	private static List<VoiceAcousticMatcher.VaultCandidate> buildVaultCandidates(boolean includeEmptySpellSlots) {
		Minecraft mc = Minecraft.getMinecraft();
		if (mc == null || mc.thePlayer == null) return null;
		VoiceVaultStorage vault = VoiceVaultStorage.For(mc.thePlayer);
		if (vault == null) return null;
		List<VoiceAcousticMatcher.VaultCandidate> list = new ArrayList<VoiceAcousticMatcher.VaultCandidate>(VoiceVaultStorage.NUM_SLOTS);
		for (int i = 0; i < VoiceVaultStorage.NUM_SLOTS; i++) {
			String w = vault.getWord(i);
			if (w == null || w.isEmpty()) continue;
			ItemStack spell = vault.getStackInSlot(i);
			if (spell != null || includeEmptySpellSlots) {
				String label = spell != null ? VoiceAcousticMatcher.describeSpell(spell) : ("Slot #" + (i + 1));
				list.add(new VoiceAcousticMatcher.VaultCandidate(i, w, label));
			}
		}
		return list;
	}

	@SubscribeEvent
	public void onClientTick(TickEvent.ClientTickEvent event) {
		if (event.phase != TickEvent.Phase.END) return;
		Minecraft mc = Minecraft.getMinecraft();
		if (mc == null || mc.thePlayer == null) {
			keyHeld = false;
			guiTestMode = false;
			return;
		}

		keyHeld = mc.currentScreen == null && AMKeyBindings.VoiceCastKey != null && AMKeyBindings.VoiceCastKey.getIsKeyPressed();

		if (matcher.consumeMigratedNotice()) {
			sendClientChat(EnumChatFormatting.AQUA + "[AM2 Voice] " + EnumChatFormatting.YELLOW
					+ "Motor vocal atualizado para v5 (MFCC)! Regrave suas palavras na Aura Vocal para separação muito superior.");
		}

		String msg;
		while ((msg = pendingChatMessages.poll()) != null) {
			sendClientChat(msg);
		}

		PendingCast cast;
		while ((cast = pendingCasts.poll()) != null) {
			dispatchCast(mc.thePlayer, cast);
		}
	}

	private void dispatchCast(EntityPlayer player, PendingCast cast) {
		if (!VocalResonance.isUnlocked(player)) {
			return;
		}
		if (AkashicVoiceBridge.isLocalPlayerSilenced()) {
			hudBannerText = EnumChatFormatting.RED + "Silenciado!";
			hudBannerUntilMillis = System.currentTimeMillis() + 1800L;
			return;
		}

		int pct = (int) Math.round(cast.score * 100.0);
		if (cast.binding.vaultSlot >= 0) {
			VoiceVaultStorage vault = VoiceVaultStorage.For(player);
			ItemStack stored = vault != null ? vault.getStackInSlot(cast.binding.vaultSlot) : null;
			if (stored == null) {
				hudBannerText = EnumChatFormatting.YELLOW + "Slot #" + (cast.binding.vaultSlot + 1) + " está vazio na Aura Vocal";
				hudBannerUntilMillis = System.currentTimeMillis() + 2000L;
				return;
			}
			String label = VoiceAcousticMatcher.describeSpell(stored);
			hudBannerText = EnumChatFormatting.AQUA + "'" + cast.binding.word + "' (" + pct + "%) -> " + EnumChatFormatting.WHITE + label;
			hudBannerUntilMillis = System.currentTimeMillis() + 2200L;
			AMNetHandler.INSTANCE.sendVoiceSpellCast(cast.binding.vaultSlot, VoiceSpellServerHandler.VAULT_CAST_MARKER, cast.binding.word);
			return;
		}

		int[] slotRef = findSpellInInventory(player, cast.binding);
		if (slotRef == null) {
			sendClientChat(EnumChatFormatting.YELLOW + "[AM2 Voice] " + EnumChatFormatting.GRAY
					+ "Reconheci '" + cast.binding.word + "', mas a magia não está no inventário.");
			return;
		}

		hudBannerText = EnumChatFormatting.AQUA + "'" + cast.binding.word + "' (" + pct + "%) -> " + EnumChatFormatting.WHITE + cast.binding.spellDisplayName;
		hudBannerUntilMillis = System.currentTimeMillis() + 2200L;
		AMNetHandler.INSTANCE.sendVoiceSpellCast(slotRef[0], slotRef[1], cast.binding.word);
	}

	public static void spawnClientVoiceCastParticles(byte[] data) {
		if (data == null) return;
		Minecraft mc = Minecraft.getMinecraft();
		if (mc == null || mc.theWorld == null) return;
		try {
			AMDataReader rdr = new AMDataReader(data, false);
			double x = rdr.getDouble();
			double y = rdr.getDouble();
			double z = rdr.getDouble();
			for (int i = 0; i < 12; i++) {
				double angle = (Math.PI * 2.0 * i) / 12.0;
				double rx = Math.cos(angle) * 0.42;
				double rz = Math.sin(angle) * 0.42;
				AMParticle p = (AMParticle) AMCore.proxy.particleManager.spawn(
						mc.theWorld, "sparkle", x + rx, y + (mc.theWorld.rand.nextDouble() - 0.5) * 0.2, z + rz);
				if (p != null) {
					p.AddParticleController(new ParticleFloatUpward(p, 0.015f, 0.03f, 1, false));
					p.setMaxAge(22);
					if ((i & 1) == 0) {
						p.setRGBColorF(0.22f, 0.92f, 1.0f);
					} else {
						p.setRGBColorF(1.0f, 0.84f, 0.32f);
					}
				}
			}
		} catch (Throwable ignored) {
		}
	}

	private static int[] findSpellInInventory(EntityPlayer player, VoiceAcousticMatcher.VoiceBinding binding) {
		int[] exact = searchInventory(player, binding, true);
		if (exact != null) return exact;
		if (!VoiceAcousticMatcher.isGenericUnnamedLabel(binding.spellDisplayName)) {
			return searchInventory(player, binding, false);
		}
		return null;
	}

	private static int[] searchInventory(EntityPlayer player, VoiceAcousticMatcher.VoiceBinding binding, boolean matchFingerprint) {
		ItemStack[] inv = player.inventory.mainInventory;
		for (int i = 0; i < inv.length; i++) {
			ItemStack stack = inv[i];
			if (stack == null) continue;
			if (stack.getItem() instanceof ItemSpellBase) {
				if (matchesSpell(stack, binding, matchFingerprint)) {
					return new int[]{i, -1};
				}
			} else if (stack.getItem() instanceof ItemSpellBook) {
				ItemStack[] scrolls = ((ItemSpellBook) stack.getItem()).getActiveScrollInventory(stack);
				if (scrolls != null) {
					for (int s = 0; s < scrolls.length; s++) {
						if (scrolls[s] != null && scrolls[s].getItem() instanceof ItemSpellBase
								&& matchesSpell(scrolls[s], binding, matchFingerprint)) {
							return new int[]{i, s};
						}
					}
				}
			}
		}
		return null;
	}

	private static boolean matchesSpell(ItemStack stack, VoiceAcousticMatcher.VoiceBinding binding, boolean matchFingerprint) {
		if (matchFingerprint) {
			return binding.spellFingerprint != null
					&& !binding.spellFingerprint.isEmpty()
					&& binding.spellFingerprint.equals(VoiceAcousticMatcher.computeSpellFingerprint(stack));
		}
		String cleanStackName = EnumChatFormatting.getTextWithoutFormattingCodes(stack.getDisplayName());
		String cleanTargetName = EnumChatFormatting.getTextWithoutFormattingCodes(binding.spellDisplayName);
		return cleanStackName != null && cleanTargetName != null
				&& cleanStackName.trim().equalsIgnoreCase(cleanTargetName.trim());
	}

	@SubscribeEvent
	public void onRenderHud(RenderGameOverlayEvent.Text event) {
		Minecraft mc = Minecraft.getMinecraft();
		if (mc == null || mc.thePlayer == null || mc.gameSettings.showDebugInfo) return;
		if (mc.currentScreen != null) return;
		if (!VocalResonance.isUnlocked(mc.thePlayer) && activeRecording == null) return;

		long now = System.currentTimeMillis();
		RecordingRequest rec = activeRecording;
		boolean silenced = AkashicVoiceBridge.isConnected() && AkashicVoiceBridge.isLocalPlayerSilenced();
		boolean listening = AkashicVoiceBridge.isConnected() && (keyHeld || continuousListen) && !AkashicVoiceBridge.isMuted() && !silenced;
		boolean showBanner = now < hudBannerUntilMillis && hudBannerText != null && !hudBannerText.isEmpty();

		if (rec == null && !listening && !silenced && !showBanner) {
			return;
		}

		FontRenderer fr = mc.fontRenderer;
		int y = 6;
		int x = 6;

		if (rec != null) {
			if (now < rec.armAtMillis) {
				fr.drawStringWithShadow(EnumChatFormatting.YELLOW + "[AM2 Voice] Prepare-se para falar '" + rec.word + "'...", x, y, 0xFFFFFF);
			} else {
				String meter = buildRmsMeter(AkashicVoiceBridge.getLatestRms(), getEffectiveVadThreshold());
				fr.drawStringWithShadow(EnumChatFormatting.GOLD + "[AM2 Voice] FALE AGORA: '" + rec.word + "' " + meter, x, y, 0xFFFFFF);
			}
			y += 11;
		} else if (silenced) {
			fr.drawStringWithShadow(EnumChatFormatting.RED + "[AM2 Voice] Silenciado (Microfone bloqueado)", x, y, 0xFFFFFF);
			y += 11;
		} else if (listening) {
			String modeTag = keyHeld ? "Foco Vocal" : "Escuta Contínua";
			String meter = buildRmsMeter(AkashicVoiceBridge.getLatestRms(), getEffectiveVadThreshold());
			fr.drawStringWithShadow(EnumChatFormatting.AQUA + "[AM2 Voice] " + modeTag + " " + meter, x, y, 0xFFFFFF);
			y += 11;
		}

		if (showBanner) {
			fr.drawStringWithShadow(String.format(Locale.ROOT, "%s[AM2 Voice] %s", EnumChatFormatting.DARK_AQUA, hudBannerText), x, y, 0xFFFFFF);
		}
	}

	public static String buildRmsMeter(double rms, double vad) {
		int bars = (int) Math.min(8, Math.round(rms * 42.0));
		boolean voiced = rms >= vad;
		StringBuilder sb = new StringBuilder();
		sb.append(EnumChatFormatting.DARK_GRAY).append('[');
		for (int i = 0; i < 8; i++) {
			if (i < bars) {
				sb.append(voiced ? (i >= 6 ? EnumChatFormatting.GOLD : EnumChatFormatting.GREEN) : EnumChatFormatting.GRAY).append('|');
			} else {
				sb.append(EnumChatFormatting.DARK_GRAY).append('.');
			}
		}
		sb.append(EnumChatFormatting.DARK_GRAY).append(']');
		return sb.toString();
	}

	public static ItemStack getHeldSpellStack(EntityPlayer player) {
		if (player == null) return null;
		ItemStack held = player.getHeldItem();
		if (held == null) return null;
		if (held.getItem() instanceof ItemSpellBase) {
			return held;
		}
		if (held.getItem() instanceof ItemSpellBook) {
			return ((ItemSpellBook) held.getItem()).GetActiveItemStack(held);
		}
		return null;
	}

	private static void sendClientChat(String text) {
		Minecraft mc = Minecraft.getMinecraft();
		if (mc != null && mc.thePlayer != null) {
			mc.thePlayer.addChatMessage(new ChatComponentText(text));
		}
	}
}
