/*
 * ------------------------------------------------------------------------
 *
 *  Copyright (C) 2003 - 2013
 *  University of Konstanz, Germany and
 *  KNIME GmbH, Konstanz, Germany
 *  Website: http://www.knime.org; Email: contact@knime.org
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License, Version 3, as
 *  published by the Free Software Foundation.
 *
 *  This program is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, see <http://www.gnu.org/licenses>.
 *
 *  Additional permission under GNU GPL version 3 section 7:
 *
 *  KNIME interoperates with ECLIPSE solely via ECLIPSE's plug-in APIs.
 *  Hence, KNIME and ECLIPSE are both independent programs and are not
 *  derived from each other. Should, however, the interpretation of the
 *  GNU GPL Version 3 ("License") under any applicable laws result in
 *  KNIME and ECLIPSE being a combined program, KNIME GMBH herewith grants
 *  you the additional permission to use and propagate KNIME together with
 *  ECLIPSE with only the license terms in place for ECLIPSE applying to
 *  ECLIPSE and the GNU GPL Version 3 applying for KNIME, provided the
 *  license terms of ECLIPSE themselves allow for the respective use and
 *  propagation of ECLIPSE together with KNIME.
 *
 *  Additional permission relating to nodes for KNIME that extend the Node
 *  Extension (and in particular that are based on subclasses of NodeModel,
 *  NodeDialog, and NodeView) and that only interoperate with KNIME through
 *  standard APIs ("Nodes"):
 *  Nodes are deemed to be separate and independent programs and to not be
 *  covered works.  Notwithstanding anything to the contrary in the
 *  License, the License does not apply to Nodes, you are not required to
 *  license Nodes under the License, and you are granted a license to
 *  prepare and propagate Nodes, in each case even if such Nodes are
 *  propagated with or for interoperation with KNIME.  The owner of a Node
 *  may freely choose the license terms applicable to such Node, including
 *  when such Node is propagated with or for interoperation with KNIME.
 * ---------------------------------------------------------------------
 *
 * Created on Sep 13, 2013 by squareys
 */
package org.knime.knip.base.nodes.seg.waehlby;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;

import org.knime.knip.base.nodes.io.kernel.structuring.SphereSetting;
import org.knime.knip.base.nodes.proc.maxfinder.MaximumFinderOp;
import org.knime.knip.core.KNIPGateway;
import org.knime.knip.core.data.algebra.ExtendedPolygon;
import org.knime.knip.core.ops.labeling.WatershedWithSheds;
import org.knime.knip.core.util.Triple;

import net.imglib2.Cursor;
import net.imglib2.IterableInterval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.gauss3.Gauss3;
import net.imglib2.algorithm.neighborhood.Neighborhood;
import net.imglib2.algorithm.neighborhood.RectangleShape;
import net.imglib2.algorithm.neighborhood.RectangleShape.NeighborhoodsAccessible;
import net.imglib2.converter.Converter;
import net.imglib2.converter.read.ConvertedRandomAccessible;
import net.imglib2.exception.IncompatibleTypeException;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.labeling.Labeling;
import net.imglib2.ops.img.UnaryOperationBasedConverter;
import net.imglib2.ops.operation.BinaryObjectFactory;
import net.imglib2.ops.operation.BinaryOutputOperation;
import net.imglib2.ops.operation.randomaccessibleinterval.unary.DistanceMap;
import net.imglib2.ops.operation.randomaccessibleinterval.unary.morph.DilateGray;
import net.imglib2.ops.operation.randomaccessibleinterval.unary.regiongrowing.AbstractRegionGrowing;
import net.imglib2.ops.operation.randomaccessibleinterval.unary.regiongrowing.CCA;
import net.imglib2.ops.operation.real.unary.RealUnaryOperation;
import net.imglib2.outofbounds.OutOfBoundsBorderFactory;
import net.imglib2.roi.Regions;
import net.imglib2.roi.labeling.ImgLabeling;
import net.imglib2.roi.labeling.LabelRegion;
import net.imglib2.roi.labeling.LabelRegions;
import net.imglib2.roi.labeling.LabelingType;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.ShortType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Util;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

/**
 * WaehlbySplitterOp
 *
 * Performs split and merge operation similar to Wählby's method presented in "Combining intensity, edfge and shape
 * information for 2D and 3D segmentation of cell nuclei in tissue selections", 2004 and also very similar to the method
 * of cellcognizer (cellcognition.org)
 *
 * @author Jonathan Hale (University of Konstanz)
 *
 * @param <L> Type of the input {@link Labeling}<L>
 * @param <T> Type of the input {@link RandomAccessibleInterval}<T>
 */
public class WaehlbySplitterOp<L extends Comparable<L>, T extends RealType<T>> implements
        BinaryOutputOperation<RandomAccessibleInterval<LabelingType<L>>, RandomAccessibleInterval<T>, RandomAccessibleInterval<LabelingType<String>>> {

    /**
     * Segmentation type enum
     *
     * @author Jonathan Hale (University of Konstanz)
     */
    public enum SEG_TYPE {
        /**
         * Shape based segmentation.
         */
        SHAPE_BASED_SEGMENTATION, /**
                                   * Segmentation without Distance Transformation
                                   */
        OTHER_SEGMENTATION
    }

    /** Type of segmentation to be performed */
    private SEG_TYPE m_segType;

    private final int m_gaussSize;

    /** minimal Size of Objects */
    private final int m_minMergeSize;

    /** Minimal allowed object distance */
    private final int m_seedDistanceThreshold;

    private final CCA<BitType> m_cca;

    private WatershedWithSheds<FloatType, Integer> m_watershed;

    final static RectangleShape RECTANGLE_SHAPE = new RectangleShape(1, true); //"true" skips center point

    /**
     * Contructor for WaehlbySplitter operation.
     *
     * @param segType {@link SEG_TYPE} Type of segmentation to be used.
     * @param seedDistanceThreshold
     * @param mergeSizeThreshold
     * @param gaussSize
     */
    public WaehlbySplitterOp(final SEG_TYPE segType, final int seedDistanceThreshold, final int mergeSizeThreshold,
                             final int gaussSize) {
        this(segType, seedDistanceThreshold, mergeSizeThreshold, gaussSize,
                new CCA<BitType>(AbstractRegionGrowing.get8ConStructuringElement(2), new BitType()),
                new WatershedWithSheds<FloatType, Integer>(AbstractRegionGrowing.get8ConStructuringElement(2)));
    }

    /**
     * Contructor for WaehlbySplitter operation.
     *
     * @param segType {@link SEG_TYPE} Type of segmentation to be used.
     * @param seedDistanceThreshold
     * @param mergeSizeThreshold
     * @param gaussSize
     */
    protected WaehlbySplitterOp(final SEG_TYPE segType, final int seedDistanceThreshold, final int mergeSizeThreshold,
                                final int gaussSize, final CCA<BitType> cca,
                                final WatershedWithSheds<FloatType, Integer> watershed) {
        super();
        m_segType = segType;
        m_cca = cca;
        m_watershed = watershed;
        m_seedDistanceThreshold = seedDistanceThreshold;
        m_gaussSize = gaussSize;
        m_minMergeSize = mergeSizeThreshold;
    }

    private final ArrayImgFactory<FloatType> m_floatFactory = new ArrayImgFactory<FloatType>();

    /**
     * {@inheritDoc}
     */
    @Override
    public RandomAccessibleInterval<LabelingType<String>>
           compute(final RandomAccessibleInterval<LabelingType<L>> inLab, final RandomAccessibleInterval<T> img,
                   final RandomAccessibleInterval<LabelingType<String>> outLab) {

        Img<FloatType> imgAlice = m_floatFactory.create(img, new FloatType());
        Img<FloatType> imgBob = m_floatFactory.create(img, new FloatType());

        RandomAccessibleInterval<FloatType> imgAliceExt = Views.interval(Views.extendBorder(imgAlice), img);
        RandomAccessibleInterval<FloatType> imgBobExt = Views.interval(Views.extendBorder(imgBob), img);

        //Labeling converted to BitType
        RandomAccessibleInterval<BitType> inLabMasked =
                Views.interval(new ConvertedRandomAccessible<LabelingType<L>, BitType>(inLab,
                        new LabelingToBitConverter<LabelingType<L>>(), new BitType()), inLab);

        double[] sigmas = new double[inLab.numDimensions()];
        Arrays.fill(sigmas, m_gaussSize);

        if (m_segType == SEG_TYPE.SHAPE_BASED_SEGMENTATION) {
            new DistanceMap<BitType>().compute(inLabMasked, imgBobExt);
            try {
                Gauss3.gauss(sigmas, imgBobExt, imgAliceExt, 1);
            } catch (IncompatibleTypeException e) {
            }
        } else {
            try {
                Gauss3.gauss(sigmas, Views.extendBorder(img), imgAliceExt, 1);
            } catch (IncompatibleTypeException e) {
            }
        }

        // disc dilation TODO: one could expose the '3' as setting
        new DilateGray<FloatType>(new SphereSetting(2, 3).get()[0],
                new OutOfBoundsBorderFactory<FloatType, RandomAccessibleInterval<FloatType>>()).compute(imgAliceExt,
                                                                                                        imgBob);

        ImgLabeling<Integer, ShortType> seeds =
                new ImgLabeling<Integer, ShortType>(new ArrayImgFactory<ShortType>().create(img, new ShortType()));

        Img<BitType> imgChris = new ArrayImgFactory<BitType>().create(img, new BitType());
        new MaximumFinderOp<FloatType>(0, 0).compute(imgBob, imgChris);

        m_cca.compute(imgChris, seeds);

        RandomAccessibleInterval<LabelingType<String>> watershedResult =
                new ImgLabeling<String, ShortType>(new ArrayImgFactory<ShortType>().create(img, new ShortType()));
        /* Seeded Watershed */

        m_watershed.compute(invertImg(imgAlice, new FloatType()), seeds, watershedResult);

        // use the sheds from the watershed to split the labeled objects
        split("Watershed", watershedResult, inLabMasked);

        MooreContourExtractionOp contourExtraction = new MooreContourExtractionOp(true);
        ArrayImgFactory<BitType> bitFactory = new ArrayImgFactory<BitType>();
        ArrayList<LabeledObject> objects = new ArrayList<LabeledObject>();

        LabelRegions<String> regions = KNIPGateway.regions().regions(watershedResult);
        final ArrayList<String> labels = new ArrayList<String>(regions.getExistingLabels());
        Collections.sort(labels, new Comparator<String>() {

            @Override
            public int compare(final String o1, final String o2) {
                return o1.compareTo(o2) * -1;
            }
        });

        //                 Get some more information about the objects. Contour, bounding box etc
        for (final String label : labels) {

            IterableInterval<LabelingType<String>> intervalOverSrc =
                    Regions.sample(regions.getLabelRegion(label), watershedResult);

            //Create individual images for every object
            final ExtendedPolygon poly = new ExtendedPolygon();

            //compute contour polygon
            contourExtraction.compute(regions.getLabelRegion(label), poly);

            objects.add(new LabeledObject(poly, label, intervalOverSrc));
        }

        Cursor<LabelingType<String>> outC = Views.iterable(outLab).cursor();
        Cursor<LabelingType<String>> inC = Views.iterable(watershedResult).cursor();

        while (inC.hasNext()) {
            outC.next().addAll(inC.next());
        }

        final ArrayList<Triple<LabeledObject, LabeledObject, String>> mergedList =
                new ArrayList<Triple<LabeledObject, LabeledObject, String>>();

        { /*scope for object pair finding */
            ArrayList<int[]> points = new ArrayList<int[]>();

            boolean found = false;

            NeighborhoodsAccessible<LabelingType<String>> raNeighWatershed =
                    RECTANGLE_SHAPE.neighborhoodsRandomAccessibleSafe(Views.interval(
                                                                                     Views.extendValue(watershedResult,
                                                                                                       Util.getTypeFromInterval(watershedResult)
                                                                                                               .createVariable()),
                                                                                     watershedResult));

            // find two objects that are closer than rSize
            for (int i = 0; i < objects.size(); ++i) {
                for (int j = i + 1; j < objects.size(); ++j) {

                    final LabeledObject iObj = objects.get(i);
                    final LabeledObject jObj = objects.get(j);

                    final ExtendedPolygon iPoly = iObj.getContour();
                    final ExtendedPolygon jPoly = jObj.getContour();

                    final long[] jCenter = jPoly.getCenter();
                    final long[] iCenter = iPoly.getCenter();

                    final double diffX =
                            (iCenter[0] + iPoly.getBounds2D().getX()) - (jCenter[0] + jPoly.getBounds2D().getX());
                    final double diffY =
                            (iCenter[1] + iPoly.getBounds2D().getY()) - (jCenter[1] + jPoly.getBounds2D().getY());

                    if ((diffX * diffX + diffY * diffY) < m_seedDistanceThreshold) {
                        found = false; //reset flag

                        // find two points close to each other
                        for (int[] iPoint : iPoly) {
                            if (found) {
                                break;
                            }

                            for (int[] jPoint : jPoly) {
                                if (distanceSq(iPoint, jPoint) < 4) {
                                    found = true;

                                    points.add(iPoint);
                                    points.add(jPoint);
                                }
                            }
                        }

                        if (found) {
                            final String ijLabel = objects.get(j).getLabel();

                            final Cursor<LabelingType<String>> curs =
                                    iObj.getIterableIntervalOverWatershedResult().cursor();

                            final RandomAccess<LabelingType<String>> raWatershed = watershedResult.randomAccess();

                            //overwrite i to have label of j
                            while (curs.hasNext()) {
                                LabelingType<String> next = curs.next();
                                next.clear();
                                next.add(ijLabel);
                            }

                            //set label of all points
                            for (int[] point : points) {
                                raWatershed.setPosition(point);
                                raWatershed.get().clear();
                                raWatershed.get().add(ijLabel);
                            }

                            //fill remaining gap for both points
                            for (int[] point : points) {
                                remainingGapFill(raNeighWatershed.randomAccess(watershedResult), point, ijLabel);
                            }

                            mergedList.add(new Triple<LabeledObject, LabeledObject, String>(iObj, jObj, ijLabel));

                            // transitive dependencies need to be resolved
                            HashSet<Triple<LabeledObject, LabeledObject, String>> toRemove =
                                    new HashSet<Triple<LabeledObject, LabeledObject, String>>();
                            for (Triple<LabeledObject, LabeledObject, String> triple : mergedList) {
                                if (triple.getThird().equals(iObj.m_label)) {
                                    toRemove.add(triple);
                                }
                            }

                            mergedList.removeAll(toRemove);
                            points.clear();
                        }
                    }
                }
            }
        } /*end scope for object finding */

        RandomAccess<LabelingType<String>> raOut = outLab.randomAccess();

        regions = KNIPGateway.regions().regions(watershedResult);
        // Decide on circularity and size whether to actually merge the found objects
        for (Triple<LabeledObject, LabeledObject, String> triple : mergedList) {
            final String label = triple.getThird();

            final LabelRegion<String> roi = regions.getLabelRegion(label);

            final IterableInterval<LabelingType<String>> intervalOverSrc = Regions.sample(roi, watershedResult);

            final ExtendedPolygon poly = new ExtendedPolygon();

            //compute contour polygon
            contourExtraction.compute(roi, poly);

            long iSize = triple.getFirst().getIterableIntervalOverWatershedResult().size();
            long jSize = triple.getSecond().getIterableIntervalOverWatershedResult().size();

            //decide whether to actually merge the objects
            double iCircularity = featureCircularity(triple.getFirst().getPerimeter(), iSize);
            double jCircularity = featureCircularity(triple.getSecond().getPerimeter(), jSize);
            double ijCircularity = featureCircularity(poly.length(), intervalOverSrc.size());

            final Cursor<LabelingType<String>> curs = intervalOverSrc.cursor();
            if (!(iCircularity < ijCircularity || jCircularity < ijCircularity) || iSize < m_minMergeSize
                    || jSize < m_minMergeSize) {
                curs.reset();

                while (curs.hasNext()) {
                    curs.fwd();
                    raOut.setPosition(curs);
                    raOut.get().clear();
                    raOut.get().add(label);
                }
            }
        }

        return watershedResult;

    }

    private final long[] add2D(final long[] a, final long[] b) {
        return new long[]{a[0] + b[0], a[1] + b[1]};
    }

    private final double featureCircularity(final double perimeter, final double roisize) {
        return perimeter / (2.0 * Math.sqrt(Math.PI * roisize));
    }

    private final void remainingGapFill(final RandomAccess<Neighborhood<LabelingType<String>>> ra, final int[] point,
                                        final String label) {
        ra.setPosition(point);
        final Cursor<LabelingType<String>> cNeigh = ra.get().cursor().copyCursor();

        int numNonij;
        boolean leq2;

        while (cNeigh.hasNext()) {
            final LabelingType<String> p = cNeigh.next();

            if (!p.contains(label)) {
                numNonij = 0;
                leq2 = true;

                ra.setPosition(cNeigh);
                Cursor<LabelingType<String>> curs = ra.get().cursor();

                while (curs.hasNext()) {
                    if (!curs.next().contains(label)) {
                        ++numNonij;

                        if (numNonij > 2) {
                            leq2 = false;
                            break;
                        }
                    }
                }

                if (leq2) {
                    p.clear();
                    p.add(label);
                }
            }
        }
    }

    private final int distanceSq(final int[] iPoint, final int[] jPoint) {
        final int a = jPoint[0] - iPoint[0];
        final int b = jPoint[1] - iPoint[1];
        return (a * a + b * b);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BinaryObjectFactory<RandomAccessibleInterval<LabelingType<L>>, RandomAccessibleInterval<T>, RandomAccessibleInterval<LabelingType<String>>>
           bufferFactory() {
        return new BinaryObjectFactory<RandomAccessibleInterval<LabelingType<L>>, RandomAccessibleInterval<T>, RandomAccessibleInterval<LabelingType<String>>>() {
            @Override
            public RandomAccessibleInterval<LabelingType<String>> instantiate(
                                                                              final RandomAccessibleInterval<LabelingType<L>> lab,
                                                                              final RandomAccessibleInterval<T> in) {
                return KNIPGateway.ops().create().imgLabeling(lab);
            }
        };
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BinaryOutputOperation<RandomAccessibleInterval<LabelingType<L>>, RandomAccessibleInterval<T>, RandomAccessibleInterval<LabelingType<String>>>
           copy() {
        return new WaehlbySplitterOp<L, T>(m_segType, m_seedDistanceThreshold, m_minMergeSize, m_gaussSize,
                (CCA<BitType>)m_cca.copy(), (WatershedWithSheds<FloatType, Integer>)m_watershed.copy());
    }

    /**
     * Helper class containing a contour polygon, label and topleft point of bounding box of a labeled object
     *
     * @author Jonathan Hale (University of Konstanz)
     */
    private class LabeledObject {
        final ExtendedPolygon m_contour;

        final String m_label;

        final IterableInterval<LabelingType<String>> m_overSrc;

        public LabeledObject(final ExtendedPolygon c, final String l,
                             final IterableInterval<LabelingType<String>> oSrc) {

            m_contour = c;
            m_label = l;
            m_overSrc = oSrc;
        }

        public final ExtendedPolygon getContour() {
            return m_contour;
        }

        public final int getPerimeter() {
            return m_contour.length();
        }

        public final String getLabel() {
            return m_label;
        }

        public final IterableInterval<LabelingType<String>> getIterableIntervalOverWatershedResult() {
            return m_overSrc;
        }
    }

    private <RT extends RealType<RT>> IntervalView<RT> invertImg(final RandomAccessibleInterval<RT> rai,
                                                                 final RT type) {
        return Views.interval(
                              new ConvertedRandomAccessible<RT, RT>(rai,
                                      new UnaryOperationBasedConverter<RT, RT>(new SignedRealInvert<RT, RT>()), type),
                              rai);
    }

    /**
     * Inverter for signed RealTypes
     *
     * @author Christian Dietz (University of Konstanz)
     * @param <I>
     * @param <O>
     */
    private class SignedRealInvert<I extends RealType<I>, O extends RealType<O>> implements RealUnaryOperation<I, O> {

        @Override
        public O compute(final I x, final O output) {
            final double value = x.getRealDouble() * -1.0;
            output.setReal(value);
            return output;
        }

        @Override
        public SignedRealInvert<I, O> copy() {
            return new SignedRealInvert<I, O>();
        }

    }

    private class LabelingToBitConverter<LT extends LabelingType<?>> implements Converter<LT, BitType> {
        /**
         * {@inheritDoc}
         */
        @Override
        public void convert(final LT input, final BitType output) {
            output.set(!input.isEmpty());
        }

    }

    private void split(final String shedLabel, final RandomAccessibleInterval<LabelingType<String>> watershedResult,
                       final RandomAccessibleInterval<BitType> inLabMasked) {
        Cursor<LabelingType<String>> cursor = Views.iterable(watershedResult).cursor();
        RandomAccess<BitType> ra = inLabMasked.randomAccess();

        while (cursor.hasNext()) {
            LabelingType<String> type = cursor.next();

            ra.setPosition(cursor);

            if (type.contains(shedLabel) || !ra.get().get()) {
                type.clear();
            }
        }

    }
}
