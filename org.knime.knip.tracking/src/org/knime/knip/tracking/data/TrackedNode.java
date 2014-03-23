package org.knime.knip.tracking.data;

import java.util.Map;

import net.imglib2.RealPoint;
import net.imglib2.meta.ImgPlus;
import net.imglib2.type.logic.BitType;
import fiji.plugin.trackmate.FeatureHolder;
import fiji.plugin.trackmate.tracking.TrackableObject;

public class TrackedNode<L extends Comparable<L>> implements
		Comparable<TrackedNode<L>>, TrackableObject, FeatureHolder {

	private final RealPoint m_point;
	private final Map<String, Double> m_features;
	private final L m_label;
	private int m_timeIdx;
	private final ImgPlus<BitType> m_bitMask;
	private final long[] m_offset;
	private boolean isVisible = true;

	public TrackedNode(ImgPlus<BitType> bitMask, long[] offset, L label,
			int timeIdx, Map<String, Double> features) {
		m_features = features;
		m_label = label;
		m_timeIdx = timeIdx;
		m_bitMask = bitMask;
		m_offset = offset;

		double[] centroid = new BitMaskCentroid(offset).compute(bitMask,
				new double[bitMask.numDimensions()]);

		for (int d = 0; d < centroid.length; d++) {
			centroid[d] += offset[d];
		}

		m_point = new RealPoint(centroid);
	}

	@Override
	public int compareTo(TrackedNode<L> o) {
		return m_label.compareTo(o.m_label);
	}

	@SuppressWarnings("unchecked")
	@Override
	public boolean equals(Object obj) {
		if (obj instanceof TrackedNode) {
			return m_label.equals(((TrackedNode<L>) obj).m_label);
		}
		return false;
	}

	// same as above
	@Override
	public int hashCode() {
		return m_label.hashCode();
	}

	@Override
	public void localize(float[] position) {
		m_point.localize(position);
	}

	@Override
	public void localize(double[] position) {
		m_point.localize(position);
	}

	@Override
	public float getFloatPosition(int d) {
		return m_point.getFloatPosition(d);
	}

	@Override
	public double getDoublePosition(int d) {
		return m_point.getDoublePosition(d);
	}

	@Override
	public int numDimensions() {
		return m_point.numDimensions();
	}

	@Override
	public int frame() {
		return (int) m_point.getFloatPosition(m_timeIdx);
	}

	@Override
	public int ID() {
		return m_label.hashCode();
	}

	public ImgPlus<BitType> bitMask() {
		return m_bitMask;
	}

	public L label() {
		return m_label;
	}

	public long offset(int d) {
		return m_offset[d];
	}

	@Override
	public String getName() {
		return m_bitMask.toString();
	}

	@Override
	public boolean isVisible() {
		return isVisible;
	}

	@Override
	public double radius() {
		// TODO find better solution here
		return Math.max(m_bitMask.dimension(0), m_bitMask.dimension(1));
	}

	@Override
	public void setFrame(int frame) {
		m_point.setPosition(frame, m_timeIdx);
	}

	@Override
	public void setName(String name) {
		throw new UnsupportedOperationException("can't set name");
	}

	@Override
	public void setVisible(boolean isVisible) {
		this.isVisible = isVisible;
	}

	@Override
	public Double getFeature(String feature) {
		return m_features.get(feature);
	}

	@Override
	public Map<String, Double> getFeatures() {
		return m_features;
	}

	@Override
	public void putFeature(String feature, Double value) {
		m_features.put(feature, value);
	}
}
