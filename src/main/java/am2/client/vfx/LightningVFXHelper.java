package am2.client.vfx;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.renderer.Tessellator;

import java.util.Random;

/**
 * Shared client-only geometry and batched Tessellator emitters for heavy-storm
 * lightning spells (Orbital Storm and Lightning Strike).
 *
 * All emitters write directly into an already-started Tessellator batch so
 * hundreds of bolts, forks, rings, sparks, and plasma columns draw in a handful
 * of OpenGL calls with zero GL_LINE_STRIP / GL_LINE_SMOOTH overhead.
 */
@SideOnly(Side.CLIENT)
public final class LightningVFXHelper {

	public static final int RING_SEGS_HIGH = 24;
	public static final int RING_SEGS_MED = 16;

	private static final double[] COS_24 = new double[RING_SEGS_HIGH + 1];
	private static final double[] SIN_24 = new double[RING_SEGS_HIGH + 1];
	private static final double[] COS_16 = new double[RING_SEGS_MED + 1];
	private static final double[] SIN_16 = new double[RING_SEGS_MED + 1];

	// Reusable per-frame scratch arrays (render thread only) to eliminate GC pressure.
	private static final int MAX_POLY_VERTS = 64;
	private static final double[] SCRATCH_X = new double[MAX_POLY_VERTS];
	private static final double[] SCRATCH_Y = new double[MAX_POLY_VERTS];
	private static final double[] SCRATCH_Z = new double[MAX_POLY_VERTS];
	private static final double[] SCRATCH_NX = new double[MAX_POLY_VERTS];
	private static final double[] SCRATCH_NY = new double[MAX_POLY_VERTS];
	private static final double[] SCRATCH_NZ = new double[MAX_POLY_VERTS];

	static {
		for (int i = 0; i <= RING_SEGS_HIGH; i++) {
			double a = i * (Math.PI * 2.0 / RING_SEGS_HIGH);
			COS_24[i] = Math.cos(a);
			SIN_24[i] = Math.sin(a);
		}
		for (int i = 0; i <= RING_SEGS_MED; i++) {
			double a = i * (Math.PI * 2.0 / RING_SEGS_MED);
			COS_16[i] = Math.cos(a);
			SIN_16[i] = Math.sin(a);
		}
	}

	private LightningVFXHelper() {}

	/**
	 * Pre-computed jagged lightning trunk + tapered forked branches.
	 * Generated once at bolt spawn so rendering performs zero Random/array allocations.
	 */
	public static final class BoltPath {
		public final double[] x;
		public final double[] y;
		public final double[] z;
		public final double[][] forkX;
		public final double[][] forkY;
		public final double[][] forkZ;
		public final float[] forkAttachFrac;
		public final float[] forkWidthRatio;

		public BoltPath(
				double[] x, double[] y, double[] z,
				double[][] forkX, double[][] forkY, double[][] forkZ,
				float[] forkAttachFrac, float[] forkWidthRatio
		) {
			this.x = x;
			this.y = y;
			this.z = z;
			this.forkX = forkX;
			this.forkY = forkY;
			this.forkZ = forkZ;
			this.forkAttachFrac = forkAttachFrac;
			this.forkWidthRatio = forkWidthRatio;
		}
	}

	/**
	 * Fills a 12-float palette for high-contrast heavy-storm lightning:
	 * [0..2]  deep storm-cobalt outer halo
	 * [3..5]  sharp ice-blue electric plasma
	 * [6..8]  dry crisp white incandescent core
	 * [9..11] dark brooding storm-cloud tint
	 */
	public static void resolvePalette(int customColor, float[] out) {
		if (customColor == -1) {
			out[0] = 0.08f; out[1] = 0.28f; out[2] = 0.82f;
			out[3] = 0.40f; out[4] = 0.78f; out[5] = 1.00f;
			out[6] = 0.95f; out[7] = 0.99f; out[8] = 1.00f;
			out[9] = 0.075f; out[10] = 0.095f; out[11] = 0.145f;
			return;
		}
		float r = ((customColor >> 16) & 0xFF) / 255.0f;
		float g = ((customColor >> 8) & 0xFF) / 255.0f;
		float b = (customColor & 0xFF) / 255.0f;

		// Ensure dark custom colors still read cleanly as electrical energy.
		float luma = r * 0.299f + g * 0.587f + b * 0.114f;
		float lift = luma < 0.25f ? (0.25f - luma) * 0.7f : 0.0f;
		float mr = clamp01(r + lift);
		float mg = clamp01(g + lift);
		float mb = clamp01(b + lift);

		out[0] = clamp01(mr * 0.42f);
		out[1] = clamp01(mg * 0.42f);
		out[2] = clamp01(mb * 0.42f);

		out[3] = clamp01(mr * 0.85f + 0.15f);
		out[4] = clamp01(mg * 0.85f + 0.15f);
		out[5] = clamp01(mb * 0.85f + 0.15f);

		out[6] = clamp01(mr * 0.28f + 0.72f);
		out[7] = clamp01(mg * 0.28f + 0.72f);
		out[8] = clamp01(mb * 0.28f + 0.72f);

		out[9] = clamp01(0.055f + mr * 0.13f);
		out[10] = clamp01(0.060f + mg * 0.13f);
		out[11] = clamp01(0.080f + mb * 0.15f);
	}

	/**
	 * Generates a sharp, natural, heavy-storm jagged lightning bolt with
	 * multi-scale angular kinks and tapered descending forks.
	 */
	public static BoltPath generateBolt(
			double sx, double sy, double sz,
			double ex, double ey, double ez,
			int segments, double maxJitter,
			int forkCount, boolean allowSubFork,
			Random rnd
	) {
		int segs = Math.max(4, Math.min(24, segments));
		double[] px = new double[segs + 1];
		double[] py = new double[segs + 1];
		double[] pz = new double[segs + 1];

		px[0] = sx; py[0] = sy; pz[0] = sz;
		px[segs] = ex; py[segs] = ey; pz[segs] = ez;

		double dx = ex - sx;
		double dy = ey - sy;
		double dz = ez - sz;
		double totalLen = Math.sqrt(dx * dx + dy * dy + dz * dz);

		// Two-scale angular kink (coarse main bend + sharp local step zig-zag)
		double coarseX = (rnd.nextDouble() - 0.5) * maxJitter * 1.15;
		double coarseZ = (rnd.nextDouble() - 0.5) * maxJitter * 1.15;
		double walkX = 0.0;
		double walkZ = 0.0;

		for (int k = 1; k < segs; k++) {
			double f = (double) k / (double) segs;
			double env = Math.sin(f * Math.PI);
			// Sharp realistic kinks rather than smooth sine curves
			walkX = walkX * 0.42 + (rnd.nextDouble() - 0.5) * 2.0 * maxJitter * 0.68;
			walkZ = walkZ * 0.42 + (rnd.nextDouble() - 0.5) * 2.0 * maxJitter * 0.68;
			double bendY = (rnd.nextDouble() - 0.5) * maxJitter * 0.35 * env;

			px[k] = sx + dx * f + (coarseX * env + walkX * env);
			py[k] = sy + dy * f + bendY;
			pz[k] = sz + dz * f + (coarseZ * env + walkZ * env);
		}

		int primaryForks = Math.max(0, Math.min(4, forkCount));
		int totalForks = primaryForks + (allowSubFork && primaryForks > 0 ? 1 : 0);
		double[][] fx = new double[totalForks][];
		double[][] fy = new double[totalForks][];
		double[][] fz = new double[totalForks][];
		float[] attachFrac = new float[totalForks];
		float[] widthRatio = new float[totalForks];

		double spreadBase = Math.max(2.2, Math.min(11.0, totalLen * 0.11));
		double dropBase = Math.max(3.5, Math.min(18.0, totalLen * 0.22));

		for (int fi = 0; fi < primaryForks; fi++) {
			double fracTarget = 0.22 + (fi + 0.35 + rnd.nextDouble() * 0.30) * (0.54 / Math.max(1, primaryForks));
			int attachIdx = Math.max(1, Math.min(segs - 2, (int) Math.round(fracTarget * segs)));
			attachFrac[fi] = (float) attachIdx / (float) segs;
			widthRatio[fi] = 0.46f - fi * 0.05f;

			int fSegs = 4;
			fx[fi] = new double[fSegs + 1];
			fy[fi] = new double[fSegs + 1];
			fz[fi] = new double[fSegs + 1];
			fx[fi][0] = px[attachIdx];
			fy[fi][0] = py[attachIdx];
			fz[fi][0] = pz[attachIdx];

			double forkAng = rnd.nextDouble() * Math.PI * 2.0;
			double dirX = Math.cos(forkAng);
			double dirZ = Math.sin(forkAng);
			double stepHoriz = (spreadBase * (0.65 + rnd.nextDouble() * 0.55)) / fSegs;
			double stepDrop = (dropBase * (0.60 + rnd.nextDouble() * 0.55)) / fSegs;

			for (int s = 1; s <= fSegs; s++) {
				double jitter = stepHoriz * 0.85;
				fx[fi][s] = fx[fi][s - 1] + dirX * stepHoriz + (rnd.nextDouble() - 0.5) * jitter;
				fy[fi][s] = fy[fi][s - 1] - stepDrop * (0.75 + rnd.nextDouble() * 0.5);
				fz[fi][s] = fz[fi][s - 1] + dirZ * stepHoriz + (rnd.nextDouble() - 0.5) * jitter;
				// Clamp fork slightly above ground target so it doesn't poke beneath terrain
				if (fy[fi][s] < ey + 0.35) {
					fy[fi][s] = ey + 0.35;
				}
			}
		}

		// Optional fine secondary sub-fork branching off the first primary fork
		if (totalForks > primaryForks) {
			int sub = primaryForks;
			attachFrac[sub] = Math.min(0.95f, attachFrac[0] + 0.10f);
			widthRatio[sub] = 0.28f;
			int sSegs = 3;
			fx[sub] = new double[sSegs + 1];
			fy[sub] = new double[sSegs + 1];
			fz[sub] = new double[sSegs + 1];
			fx[sub][0] = fx[0][2];
			fy[sub][0] = fy[0][2];
			fz[sub][0] = fz[0][2];

			double subAng = rnd.nextDouble() * Math.PI * 2.0;
			double stepH = spreadBase * 0.18;
			double stepD = dropBase * 0.16;
			for (int s = 1; s <= sSegs; s++) {
				fx[sub][s] = fx[sub][s - 1] + Math.cos(subAng) * stepH + (rnd.nextDouble() - 0.5) * stepH;
				fy[sub][s] = Math.max(ey + 0.35, fy[sub][s - 1] - stepD);
				fz[sub][s] = fz[sub][s - 1] + Math.sin(subAng) * stepH + (rnd.nextDouble() - 0.5) * stepH;
			}
		}

		return new BoltPath(px, py, pz, fx, fy, fz, attachFrac, widthRatio);
	}

	/**
	 * Natural heavy-storm double re-strike intensity curve:
	 * explosive main flash -> brief cooling dip -> sharp secondary surge -> clean decay.
	 */
	public static float heavyStormFlashCurve(float progress) {
		if (progress <= 0.0f) return 1.0f;
		if (progress >= 1.0f) return 0.0f;
		if (progress < 0.22f) {
			return 1.0f;
		}
		if (progress < 0.36f) {
			float u = (progress - 0.22f) / 0.14f;
			return 1.0f - 0.45f * u;
		}
		if (progress < 0.52f) {
			float u = (progress - 0.36f) / 0.16f;
			return 0.55f + 0.35f * (float) Math.sin(u * Math.PI);
		}
		float u = (progress - 0.52f) / 0.48f;
		float inv = 1.0f - u;
		return inv * inv * 0.58f;
	}

	/**
	 * Emits a complete multi-layer heavy-storm bolt (cobalt halo + ice-blue plasma + dry white core)
	 * plus its tapered forked branches directly into an active GL_TRIANGLES Tessellator batch.
	 */
	public static void emitHeavyStormBolt(
			Tessellator t,
			BoltPath path,
			double camX, double camY, double camZ,
			double baseHalfWidth,
			float[] palette,
			float alpha,
			float headFraction,
			boolean drawForks,
			boolean highDetail
	) {
		if (alpha <= 0.008f || headFraction <= 0.01f) return;

		// 1. Deep storm-cobalt atmospheric outer halo (wide & moody)
		if (highDetail) {
			emitPolylineRibbon(
					t, path.x, path.y, path.z, path.x.length,
					camX, camY, camZ,
					baseHalfWidth * 2.55, baseHalfWidth * 2.15,
					palette[0], palette[1], palette[2],
					alpha * 0.24f, alpha * 0.20f,
					headFraction
			);
		}

		// 2. Crisp ice-blue high-voltage plasma sheath
		emitPolylineRibbon(
				t, path.x, path.y, path.z, path.x.length,
				camX, camY, camZ,
				baseHalfWidth * 0.92, baseHalfWidth * 0.78,
				palette[3], palette[4], palette[5],
				alpha * 0.78f, alpha * 0.74f,
				headFraction
		);

		// 3. Dry incandescent razor-white core
		emitPolylineRibbon(
				t, path.x, path.y, path.z, path.x.length,
				camX, camY, camZ,
				baseHalfWidth * 0.28, baseHalfWidth * 0.22,
				palette[6], palette[7], palette[8],
				alpha * 0.98f, alpha * 0.95f,
				headFraction
		);

		if (!drawForks || path.forkX.length == 0) return;

		for (int fi = 0; fi < path.forkX.length; fi++) {
			float attach = path.forkAttachFrac[fi];
			if (headFraction <= attach) continue;
			float forkHead = Math.min(1.0f, (headFraction - attach) / Math.max(0.12f, 1.0f - attach));
			double fw = baseHalfWidth * path.forkWidthRatio[fi];
			float fAlpha = alpha * (fi == path.forkX.length - 1 && path.forkWidthRatio[fi] < 0.35f ? 0.65f : 0.82f);

			emitPolylineRibbon(
					t, path.forkX[fi], path.forkY[fi], path.forkZ[fi], path.forkX[fi].length,
					camX, camY, camZ,
					fw * 1.15, fw * 0.14,
					palette[3], palette[4], palette[5],
					fAlpha * 0.68f, fAlpha * 0.12f,
					forkHead
			);
			emitPolylineRibbon(
					t, path.forkX[fi], path.forkY[fi], path.forkZ[fi], path.forkX[fi].length,
					camX, camY, camZ,
					fw * 0.34, fw * 0.05,
					palette[6], palette[7], palette[8],
					fAlpha * 0.92f, fAlpha * 0.18f,
					forkHead
			);
		}
	}

	/**
	 * Emits a connected camera-facing feathered triangle ribbon along a 3D polyline.
	 * The center spine carries full alpha while both outer edges feather smoothly to 0,
	 * producing razor-clean anti-aliased lightning without GL_LINE_SMOOTH or textures.
	 */
	public static void emitPolylineRibbon(
			Tessellator t,
			double[] wx, double[] wy, double[] wz, int vertexCount,
			double camX, double camY, double camZ,
			double wStart, double wEnd,
			float r, float g, float b,
			float alphaStart, float alphaEnd,
			float headFraction
	) {
		if (vertexCount < 2 || headFraction <= 0.005f) return;
		if (alphaStart <= 0.003f && alphaEnd <= 0.003f) return;

		int segTotal = Math.min(vertexCount - 1, MAX_POLY_VERTS - 1);
		double exactSeg = Math.min(segTotal, Math.max(0.01, headFraction * segTotal));
		int fullSegs = (int) Math.floor(exactSeg);
		double rem = exactSeg - fullSegs;

		int activeCount = fullSegs + 1;
		if (rem > 0.001 && fullSegs < segTotal) {
			activeCount++;
		}
		if (activeCount < 2) return;

		for (int i = 0; i <= fullSegs; i++) {
			SCRATCH_X[i] = wx[i] - camX;
			SCRATCH_Y[i] = wy[i] - camY;
			SCRATCH_Z[i] = wz[i] - camZ;
		}
		if (activeCount > fullSegs + 1) {
			double x0 = wx[fullSegs] - camX, y0 = wy[fullSegs] - camY, z0 = wz[fullSegs] - camZ;
			double x1 = wx[fullSegs + 1] - camX, y1 = wy[fullSegs + 1] - camY, z1 = wz[fullSegs + 1] - camZ;
			SCRATCH_X[activeCount - 1] = x0 + (x1 - x0) * rem;
			SCRATCH_Y[activeCount - 1] = y0 + (y1 - y0) * rem;
			SCRATCH_Z[activeCount - 1] = z0 + (z1 - z0) * rem;
		}

		// Compute smooth camera-aligned side normals per vertex so jagged bends connect cleanly.
		for (int i = 0; i < activeCount; i++) {
			double dx, dy, dz;
			if (i == 0) {
				dx = SCRATCH_X[1] - SCRATCH_X[0];
				dy = SCRATCH_Y[1] - SCRATCH_Y[0];
				dz = SCRATCH_Z[1] - SCRATCH_Z[0];
			} else if (i == activeCount - 1) {
				dx = SCRATCH_X[i] - SCRATCH_X[i - 1];
				dy = SCRATCH_Y[i] - SCRATCH_Y[i - 1];
				dz = SCRATCH_Z[i] - SCRATCH_Z[i - 1];
			} else {
				dx = SCRATCH_X[i + 1] - SCRATCH_X[i - 1];
				dy = SCRATCH_Y[i + 1] - SCRATCH_Y[i - 1];
				dz = SCRATCH_Z[i + 1] - SCRATCH_Z[i - 1];
			}

			double mx = SCRATCH_X[i];
			double my = SCRATCH_Y[i];
			double mz = SCRATCH_Z[i];
			double nx = dz * my - dy * mz;
			double ny = dx * mz - dz * mx;
			double nz = dy * mx - dx * my;
			double len = Math.sqrt(nx * nx + ny * ny + nz * nz);
			if (len < 1e-5) {
				SCRATCH_NX[i] = 1.0;
				SCRATCH_NY[i] = 0.0;
				SCRATCH_NZ[i] = 0.0;
			} else {
				double inv = 1.0 / len;
				SCRATCH_NX[i] = nx * inv;
				SCRATCH_NY[i] = ny * inv;
				SCRATCH_NZ[i] = nz * inv;
			}
		}

		double invSpan = 1.0 / Math.max(1, segTotal);
		for (int i = 0; i < activeCount - 1; i++) {
			double t0 = Math.min(1.0, i * invSpan);
			double t1 = Math.min(1.0, ((i + 1 == activeCount - 1) ? exactSeg : (i + 1)) * invSpan);

			double w0 = wStart + (wEnd - wStart) * t0;
			double w1 = wStart + (wEnd - wStart) * t1;
			float a0 = clamp01((float) (alphaStart + (alphaEnd - alphaStart) * t0));
			float a1 = clamp01((float) (alphaStart + (alphaEnd - alphaStart) * t1));

			double ax = SCRATCH_X[i], ay = SCRATCH_Y[i], az = SCRATCH_Z[i];
			double bx = SCRATCH_X[i + 1], by = SCRATCH_Y[i + 1], bz = SCRATCH_Z[i + 1];

			double anx = SCRATCH_NX[i] * w0, any = SCRATCH_NY[i] * w0, anz = SCRATCH_NZ[i] * w0;
			double bnx = SCRATCH_NX[i + 1] * w1, bny = SCRATCH_NY[i + 1] * w1, bnz = SCRATCH_NZ[i + 1] * w1;

			// Positive feathered wing
			t.setColorRGBA_F(r, g, b, a0);
			t.addVertex(ax, ay, az);
			t.setColorRGBA_F(r, g, b, a1);
			t.addVertex(bx, by, bz);
			t.setColorRGBA_F(r, g, b, 0.0f);
			t.addVertex(bx + bnx, by + bny, bz + bnz);

			t.setColorRGBA_F(r, g, b, a0);
			t.addVertex(ax, ay, az);
			t.setColorRGBA_F(r, g, b, 0.0f);
			t.addVertex(bx + bnx, by + bny, bz + bnz);
			t.addVertex(ax + anx, ay + any, az + anz);

			// Negative feathered wing
			t.setColorRGBA_F(r, g, b, a0);
			t.addVertex(ax, ay, az);
			t.setColorRGBA_F(r, g, b, 0.0f);
			t.addVertex(bx - bnx, by - bny, bz - bnz);
			t.setColorRGBA_F(r, g, b, a1);
			t.addVertex(bx, by, bz);

			t.setColorRGBA_F(r, g, b, a0);
			t.addVertex(ax, ay, az);
			t.setColorRGBA_F(r, g, b, 0.0f);
			t.addVertex(ax - anx, ay - any, az - anz);
			t.addVertex(bx - bnx, by - bny, bz - bnz);
		}
	}

	/**
	 * Emits a smooth feathered horizontal shockwave ring in GL_TRIANGLES (inner edge alpha 0 ->
	 * spine full alpha -> outer edge alpha 0).
	 */
	public static void emitHorizontalShockRing(
			Tessellator t,
			double rx, double ry, double rz,
			double radius, double halfThickness,
			boolean highDetail,
			float r, float g, float b, float alpha
	) {
		if (alpha <= 0.005f || radius <= 0.05) return;
		double[] cos = highDetail ? COS_24 : COS_16;
		double[] sin = highDetail ? SIN_24 : SIN_16;
		int segs = highDetail ? RING_SEGS_HIGH : RING_SEGS_MED;

		double rIn = Math.max(0.02, radius - halfThickness);
		double rOut = radius + halfThickness;

		for (int i = 0; i < segs; i++) {
			double ca = cos[i], sa = sin[i];
			double cb = cos[i + 1], sb = sin[i + 1];

			double ix0 = rx + ca * rIn, iz0 = rz + sa * rIn;
			double mx0 = rx + ca * radius, mz0 = rz + sa * radius;
			double ox0 = rx + ca * rOut, oz0 = rz + sa * rOut;

			double ix1 = rx + cb * rIn, iz1 = rz + sb * rIn;
			double mx1 = rx + cb * radius, mz1 = rz + sb * radius;
			double ox1 = rx + cb * rOut, oz1 = rz + sb * rOut;

			// Inner feathered skirt
			t.setColorRGBA_F(r, g, b, 0.0f);
			t.addVertex(ix0, ry, iz0);
			t.setColorRGBA_F(r, g, b, alpha);
			t.addVertex(mx0, ry, mz0);
			t.addVertex(mx1, ry, mz1);

			t.setColorRGBA_F(r, g, b, 0.0f);
			t.addVertex(ix0, ry, iz0);
			t.setColorRGBA_F(r, g, b, alpha);
			t.addVertex(mx1, ry, mz1);
			t.setColorRGBA_F(r, g, b, 0.0f);
			t.addVertex(ix1, ry, iz1);

			// Outer feathered skirt
			t.setColorRGBA_F(r, g, b, alpha);
			t.addVertex(mx0, ry, mz0);
			t.setColorRGBA_F(r, g, b, 0.0f);
			t.addVertex(ox0, ry, oz0);
			t.addVertex(ox1, ry, oz1);

			t.setColorRGBA_F(r, g, b, alpha);
			t.addVertex(mx0, ry, mz0);
			t.setColorRGBA_F(r, g, b, 0.0f);
			t.addVertex(ox1, ry, oz1);
			t.setColorRGBA_F(r, g, b, alpha);
			t.addVertex(mx1, ry, mz1);
		}
	}

	/**
	 * Emits a 3D volumetric plasma column (tapered hourglass silhouette: wider energy flare at
	 * ground impact and cloud throat, tight pressurized plasma waist) into an active GL_TRIANGLES batch.
	 */
	public static void emitPlasmaColumn(
			Tessellator t,
			double cx, double yBottom, double yTop, double cz,
			double radiusWaist, double baseFlareMult, double topFlareMult,
			boolean highDetail,
			float r, float g, float b,
			float alphaBase, float alphaMid, float alphaTop
	) {
		if (yTop <= yBottom + 0.1) return;
		if (alphaBase <= 0.004f && alphaMid <= 0.004f && alphaTop <= 0.004f) return;

		double[] cos = highDetail ? COS_24 : COS_16;
		double[] sin = highDetail ? SIN_24 : SIN_16;
		int segs = highDetail ? RING_SEGS_HIGH : RING_SEGS_MED;

		double h = yTop - yBottom;
		double y0 = yBottom;
		double y1 = yBottom + h * 0.14;
		double y2 = yBottom + h * 0.84;
		double y3 = yTop;

		double r0 = radiusWaist * baseFlareMult;
		double r1 = radiusWaist;
		double r2 = radiusWaist * 1.06;
		double r3 = radiusWaist * topFlareMult;

		emitCylinderBand(t, cx, cz, y0, y1, r0, r1, cos, sin, segs, r, g, b, alphaBase, alphaMid);
		emitCylinderBand(t, cx, cz, y1, y2, r1, r2, cos, sin, segs, r, g, b, alphaMid, alphaMid * 0.92f);
		emitCylinderBand(t, cx, cz, y2, y3, r2, r3, cos, sin, segs, r, g, b, alphaMid * 0.92f, alphaTop);
	}

	private static void emitCylinderBand(
			Tessellator t,
			double cx, double cz,
			double yA, double yB, double radA, double radB,
			double[] cos, double[] sin, int segs,
			float r, float g, float b,
			float alphaA, float alphaB
	) {
		for (int i = 0; i < segs; i++) {
			double ca = cos[i], sa = sin[i];
			double cb = cos[i + 1], sb = sin[i + 1];

			double ax0 = cx + ca * radA, az0 = cz + sa * radA;
			double ax1 = cx + cb * radA, az1 = cz + sb * radA;
			double bx0 = cx + ca * radB, bz0 = cz + sa * radB;
			double bx1 = cx + cb * radB, bz1 = cz + sb * radB;

			t.setColorRGBA_F(r, g, b, alphaA);
			t.addVertex(ax0, yA, az0);
			t.addVertex(ax1, yA, az1);
			t.setColorRGBA_F(r, g, b, alphaB);
			t.addVertex(bx1, yB, bz1);

			t.setColorRGBA_F(r, g, b, alphaA);
			t.addVertex(ax0, yA, az0);
			t.setColorRGBA_F(r, g, b, alphaB);
			t.addVertex(bx1, yB, bz1);
			t.addVertex(bx0, yB, bz0);
		}
	}

	/**
	 * Emits a UV-mapped camera-facing billboard quad directly into an active GL_QUADS batch
	 * using camera right/up vectors (zero glPushMatrix/glRotatef).
	 */
	public static void emitBillboardQuadUV(
			Tessellator t,
			double x, double y, double z,
			double halfSize,
			double rightX, double rightZ,
			double upX, double upY, double upZ,
			double cosRoll, double sinRoll,
			float r, float g, float b, float alpha
	) {
		if (alpha <= 0.004f || halfSize <= 0.001) return;

		double bxX = (rightX * cosRoll + upX * sinRoll) * halfSize;
		double bxY = (upY * sinRoll) * halfSize;
		double bxZ = (rightZ * cosRoll + upZ * sinRoll) * halfSize;

		double byX = (-rightX * sinRoll + upX * cosRoll) * halfSize;
		double byY = (upY * cosRoll) * halfSize;
		double byZ = (-rightZ * sinRoll + upZ * cosRoll) * halfSize;

		t.setColorRGBA_F(r, g, b, alpha);
		t.addVertexWithUV(x - bxX - byX, y - bxY - byY, z - bxZ - byZ, 0.0, 1.0);
		t.addVertexWithUV(x + bxX - byX, y + bxY - byY, z + bxZ - byZ, 1.0, 1.0);
		t.addVertexWithUV(x + bxX + byX, y + bxY + byY, z + bxZ + byZ, 1.0, 0.0);
		t.addVertexWithUV(x - bxX + byX, y - bxY + byY, z - bxZ + byZ, 0.0, 0.0);
	}

	/**
	 * Emits an untextured camera-facing quad (e.g. scorched earth / dark storm debris)
	 * directly into an active GL_QUADS batch.
	 */
	public static void emitBillboardQuadSolid(
			Tessellator t,
			double x, double y, double z,
			double halfSize,
			double rightX, double rightZ,
			double upX, double upY, double upZ,
			double cosRoll, double sinRoll,
			float r, float g, float b, float alpha
	) {
		if (alpha <= 0.004f || halfSize <= 0.001) return;

		double bxX = (rightX * cosRoll + upX * sinRoll) * halfSize;
		double bxY = (upY * sinRoll) * halfSize;
		double bxZ = (rightZ * cosRoll + upZ * sinRoll) * halfSize;

		double byX = (-rightX * sinRoll + upX * cosRoll) * halfSize;
		double byY = (upY * cosRoll) * halfSize;
		double byZ = (-rightZ * sinRoll + upZ * cosRoll) * halfSize;

		t.setColorRGBA_F(r, g, b, alpha);
		t.addVertex(x - bxX - byX, y - bxY - byY, z - bxZ - byZ);
		t.addVertex(x + bxX - byX, y + bxY - byY, z + bxZ - byZ);
		t.addVertex(x + bxX + byX, y + bxY + byY, z + bxZ + byZ);
		t.addVertex(x - bxX + byX, y - bxY + byY, z - bxZ + byZ);
	}

	/**
	 * Emits a rotated horizontal UV-mapped quad directly into an active GL_QUADS batch.
	 */
	public static void emitHorizontalQuadUV(
			Tessellator t,
			double x, double y, double z,
			double radius, double cosRot, double sinRot,
			float r, float g, float b, float alpha
	) {
		if (alpha <= 0.004f || radius <= 0.01) return;
		double rx = cosRot * radius;
		double rz = sinRot * radius;
		double ux = -sinRot * radius;
		double uz = cosRot * radius;

		t.setColorRGBA_F(r, g, b, alpha);
		t.addVertexWithUV(x - rx + ux, y, z - rz + uz, 0.0, 1.0);
		t.addVertexWithUV(x + rx + ux, y, z + rz + uz, 1.0, 1.0);
		t.addVertexWithUV(x + rx - ux, y, z + rz - uz, 1.0, 0.0);
		t.addVertexWithUV(x - rx - ux, y, z - rz - uz, 0.0, 0.0);
	}

	public static float clamp01(float v) {
		return v < 0.0f ? 0.0f : (v > 1.0f ? 1.0f : v);
	}
}
