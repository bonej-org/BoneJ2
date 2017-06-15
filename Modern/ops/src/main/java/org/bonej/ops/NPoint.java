package org.bonej.ops;

import java.util.ArrayList;
import java.util.List;

import org.scijava.vecmath.Vector3d;

import sc.fiji.analyzeSkeleton.Vertex;

public class NPoint {
	public Vertex centre;
	public List<VectorsAngle> angles;

	public NPoint(final Vertex v, final List<VectorsAngle> vectorAngles) {
		this.centre = v;
		this.angles = vectorAngles;
	}

	public NPoint(final Vertex v) {
		this(v, new ArrayList<>());
	}

	public static class VectorsAngle {
		private Vector3d u, v;
		private double angle;

		public VectorsAngle(final Vector3d u, final Vector3d v) {
			this.u = u == null ? new Vector3d() : u;
			this.v = v == null ? new Vector3d() : v;
			this.angle = this.u.angle(this.v);
		}

		public Vector3d getU() {
			return u;
		}

		public void setU(final Vector3d u) {
			if (u == null)
				return;
			this.u = u;
			this.angle = this.u.angle(v);
		}

		public Vector3d getV() {
			return v;
		}

		public void setV(final Vector3d v) {
			if (v == null)
				return;
			this.v = v;
			this.angle = u.angle(this.v);
		}

		public double getAngle() {
			return angle;
		}
	}
}
