package am2.interop.voice;

import am2.LogHelper;
import am2.buffs.BuffList;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;

/**
 * Optional zero-compile-dependency reflection bridge to AkashicVoiceChat's minimal client hook API
 * ({@code dev.akashic.voice.api.AkashicVoiceClientHooks}).
 *
 * Maintains an adaptive ambient noise floor estimate and a 7-frame (~140 ms) pre-roll ring buffer
 * so initial consonants ("ch", "t", "f", "k") are never clipped before VAD triggers.
 */
@SideOnly(Side.CLIENT)
public final class AkashicVoiceBridge {

	public static final class CapturedFrame {
		public final short[] pcm;
		public final int sampleRate;
		public final double rms;
		public final boolean transmitting;
		public final long timestampMillis;

		public CapturedFrame(short[] pcm, int sampleRate, double rms, boolean transmitting, long timestampMillis) {
			this.pcm = pcm;
			this.sampleRate = sampleRate;
			this.rms = rms;
			this.transmitting = transmitting;
			this.timestampMillis = timestampMillis;
		}
	}

	private static final String HOOKS_CLASS = "dev.akashic.voice.api.AkashicVoiceClientHooks";
	private static final String FRAME_LISTENER_CLASS = "dev.akashic.voice.api.ClientMicFrameListener";
	private static final String FILTER_CLASS = "dev.akashic.voice.api.ClientMicTransmissionFilter";
	private static final int PRE_ROLL_CAPACITY = 7; // ~140 ms

	private static volatile boolean connected;
	private static volatile String statusReason = "Not initialized";
	private static volatile int apiVersion;
	private static volatile double latestRms;
	private static volatile double noiseFloorRms = 0.008;
	private static volatile long lastFrameMillis;
	private static volatile boolean lastFrameTransmitting;

	private static Method isMutedMethod;
	private static Method isPttEngagedMethod;

	private static final ArrayBlockingQueue<CapturedFrame> FRAME_QUEUE = new ArrayBlockingQueue<CapturedFrame>(96);
	private static final Object PRE_ROLL_LOCK = new Object();
	private static final CapturedFrame[] PRE_ROLL_RING = new CapturedFrame[PRE_ROLL_CAPACITY];
	private static int preRollHead = 0;
	private static int preRollCount = 0;

	private AkashicVoiceBridge() {
	}

	public static boolean initialize() {
		if (connected) return true;
		if (!Loader.isModLoaded("akashicvoice")) {
			statusReason = "AkashicVoiceChat mod not loaded";
			return false;
		}
		try {
			ClassLoader cl = AkashicVoiceBridge.class.getClassLoader();
			Class<?> hooksClass = Class.forName(HOOKS_CLASS, false, cl);
			Class<?> listenerIface = Class.forName(FRAME_LISTENER_CLASS, false, cl);
			Class<?> filterIface = Class.forName(FILTER_CLASS, false, cl);

			apiVersion = hooksClass.getField("API_VERSION").getInt(null);
			isMutedMethod = hooksClass.getMethod("isMuted");
			isPttEngagedMethod = hooksClass.getMethod("isPttEngaged");

			Object frameListenerProxy = Proxy.newProxyInstance(cl, new Class<?>[]{listenerIface}, new InvocationHandler() {
				@Override
				public Object invoke(Object proxy, Method method, Object[] args) {
					if ("onMicFrame".equals(method.getName()) && args != null && args.length == 4) {
						short[] pcm = (short[]) args[0];
						int sampleRate = ((Integer) args[1]).intValue();
						double rms = ((Double) args[2]).doubleValue();
						boolean transmitting = ((Boolean) args[3]).booleanValue();
						long now = System.currentTimeMillis();

						latestRms = rms;
						lastFrameMillis = now;
						lastFrameTransmitting = transmitting;
						updateNoiseFloor(rms);

						CapturedFrame frame = new CapturedFrame(pcm, sampleRate, rms, transmitting, now);
						if (VoiceSpellController.shouldAcceptFrames(now)) {
							if (!FRAME_QUEUE.offer(frame)) {
								FRAME_QUEUE.poll();
								FRAME_QUEUE.offer(frame);
							}
						} else {
							pushPreRoll(frame);
						}
					}
					return null;
				}
			});

			Object silenceFilterProxy = Proxy.newProxyInstance(cl, new Class<?>[]{filterIface}, new InvocationHandler() {
				@Override
				public Object invoke(Object proxy, Method method, Object[] args) {
					if ("allowTransmission".equals(method.getName())) {
						return Boolean.valueOf(!isLocalPlayerSilenced());
					}
					return Boolean.TRUE;
				}
			});

			Method addListener = hooksClass.getMethod("addMicFrameListener", listenerIface);
			Method addFilter = hooksClass.getMethod("addTransmissionFilter", filterIface);
			addListener.invoke(null, frameListenerProxy);
			addFilter.invoke(null, silenceFilterProxy);

			connected = true;
			statusReason = "Connected (API v" + apiVersion + ")";
			LogHelper.info("AM2 <-> AkashicVoiceChat client hooks connected (API v%d).", apiVersion);
			return true;
		} catch (ClassNotFoundException e) {
			statusReason = "AkashicVoiceChat loaded, but ClientHooks API not present in this jar";
			LogHelper.info("AkashicVoiceChat detected without ClientHooks API; AM2 voice integration inactive.");
			return false;
		} catch (Throwable t) {
			statusReason = "Hook initialization failed: " + t.getClass().getSimpleName();
			LogHelper.warn("Failed to initialize AM2 <-> AkashicVoiceChat hooks: %s", t.toString());
			return false;
		}
	}

	private static void updateNoiseFloor(double rms) {
		double floor = noiseFloorRms;
		if (rms < floor) {
			// Fast downward adaptation when room gets quieter
			floor = floor * 0.90 + rms * 0.10;
		} else if (rms < floor * 2.2) {
			// Very slow upward drift during ambient silence (ignores loud speech)
			floor = floor * 0.992 + rms * 0.008;
		}
		if (floor < 0.002) floor = 0.002;
		if (floor > 0.045) floor = 0.045;
		noiseFloorRms = floor;
	}

	public static void pushPreRoll(CapturedFrame frame) {
		synchronized (PRE_ROLL_LOCK) {
			PRE_ROLL_RING[preRollHead] = frame;
			preRollHead = (preRollHead + 1) % PRE_ROLL_CAPACITY;
			if (preRollCount < PRE_ROLL_CAPACITY) {
				preRollCount++;
			}
		}
	}

	public static List<CapturedFrame> drainPreRoll(long minTimestampMillis) {
		synchronized (PRE_ROLL_LOCK) {
			List<CapturedFrame> out = new ArrayList<CapturedFrame>(preRollCount);
			int start = (preRollHead - preRollCount + PRE_ROLL_CAPACITY) % PRE_ROLL_CAPACITY;
			for (int i = 0; i < preRollCount; i++) {
				CapturedFrame f = PRE_ROLL_RING[(start + i) % PRE_ROLL_CAPACITY];
				if (f != null && f.timestampMillis >= minTimestampMillis) {
					out.add(f);
				}
			}
			preRollCount = 0;
			preRollHead = 0;
			return out;
		}
	}

	public static void clearBuffers() {
		FRAME_QUEUE.clear();
		synchronized (PRE_ROLL_LOCK) {
			preRollCount = 0;
			preRollHead = 0;
		}
	}

	public static boolean isConnected() {
		return connected;
	}

	public static String getStatusReason() {
		return statusReason;
	}

	public static int getApiVersion() {
		return apiVersion;
	}

	public static double getLatestRms() {
		return latestRms;
	}

	public static double getNoiseFloorRms() {
		return noiseFloorRms;
	}

	public static long getLastFrameMillis() {
		return lastFrameMillis;
	}

	public static boolean isLastFrameTransmitting() {
		return lastFrameTransmitting;
	}

	public static ArrayBlockingQueue<CapturedFrame> getFrameQueue() {
		return FRAME_QUEUE;
	}

	public static boolean isMuted() {
		if (!connected || isMutedMethod == null) return false;
		try {
			return ((Boolean) isMutedMethod.invoke(null)).booleanValue();
		} catch (Throwable t) {
			return false;
		}
	}

	public static boolean isPttEngaged() {
		if (!connected || isPttEngagedMethod == null) return false;
		try {
			return ((Boolean) isPttEngagedMethod.invoke(null)).booleanValue();
		} catch (Throwable t) {
			return false;
		}
	}

	public static boolean isLocalPlayerSilenced() {
		if (BuffList.silence == null) return false;
		Minecraft mc = Minecraft.getMinecraft();
		if (mc == null) return false;
		EntityPlayer player = mc.thePlayer;
		return player != null && player.isPotionActive(BuffList.silence.id);
	}
}
