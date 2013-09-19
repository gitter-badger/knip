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
 * Created on 18.09.2013 by zinsmaie
 */
package org.knime.knip.core.ui.imgviewer.panels.providers;

import java.awt.Image;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.display.ColorTable;
import net.imglib2.display.ScreenImage;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.RealType;

import org.knime.knip.core.awt.AWTImageTools;
import org.knime.knip.core.awt.Real2GreyRenderer;
import org.knime.knip.core.awt.lookup.LookupTable;
import org.knime.knip.core.awt.parametersupport.RendererWithColorTable;
import org.knime.knip.core.awt.parametersupport.RendererWithLookupTable;
import org.knime.knip.core.awt.parametersupport.RendererWithNormalization;
import org.knime.knip.core.ui.event.EventListener;
import org.knime.knip.core.ui.imgviewer.events.ImgAndLabelingChgEvent;
import org.knime.knip.core.ui.imgviewer.events.ImgWithMetadataChgEvent;
import org.knime.knip.core.ui.imgviewer.events.NormalizationParametersChgEvent;
import org.knime.knip.core.ui.imgviewer.panels.transfunc.BundleChgEvent;
import org.knime.knip.core.ui.imgviewer.panels.transfunc.LookupTableChgEvent;

/**
 * @author <a href="mailto:dietzc85@googlemail.com">Christian Dietz</a>
 * @author <a href="mailto:horn_martin@gmx.de">Martin Horn</a>
 * @author <a href="mailto:michael.zinsmaier@googlemail.com">Michael Zinsmaier</a>
 * @param <T>
 */
public class ImageRU<T extends RealType<T>> extends AbstractDefaultRU<T> {

    /**
     * A simple class that can be injected in the converter so that we will always get some result.
     */
    private class SimpleTable implements LookupTable<T, ARGBType> {
        @Override
        public final ARGBType lookup(final T value) {
            return new ARGBType(1);
        }
    }

    private static final long serialVersionUID = 1L;

    private int m_hashOfLastRendering;

    private Image m_lastImage;

    private LookupTable<T, ARGBType> m_lookupTable = new SimpleTable();

    private NormalizationParametersChgEvent m_normalizationParameters = new NormalizationParametersChgEvent(0, false);

    private ColorTable[] m_colorTables = new ColorTable[]{};

    private RandomAccessibleInterval<T> m_src;

    private final boolean m_enforceGreyScale;

    private Real2GreyRenderer<T> m_greyRenderer;

    public ImageRU() {
        this(false);
    }

    /**
     * @param service
     * @param enforceGreyScale
     */
    public ImageRU(final boolean enforceGreyScale) {
        //grey mode exists to support image rendering beneath labels
        m_enforceGreyScale = enforceGreyScale;
        m_greyRenderer = new Real2GreyRenderer<T>();
    }

    @SuppressWarnings("unchecked")
    @Override
    public Image createImage() {
        if (m_lastImage != null && m_hashOfLastRendering == generateHashCode()) {
            return m_lastImage;
        }

        //converted version is guaranteed to be ? extends RealType => getNormalization and render should work
        //TODO find a way to relax type constraints from R extends RealType  to RealType

        @SuppressWarnings("rawtypes")
        RandomAccessibleInterval convertedSrc = AWTImageProvider.convertIfDouble(m_src);
        final double[] normParams = m_normalizationParameters.getNormalizationParameters(convertedSrc, m_sel);

        if (m_renderer instanceof RendererWithNormalization) {
            ((RendererWithNormalization)m_renderer).setNormalizationParameters(normParams[0], normParams[1]);
        }

        if (m_renderer instanceof RendererWithLookupTable) {
            ((RendererWithLookupTable<T, ARGBType>)m_renderer).setLookupTable(m_lookupTable);
        }

        if (m_renderer instanceof RendererWithColorTable) {
            ((RendererWithColorTable)m_renderer).setColorTables(m_colorTables);
        }

        final ScreenImage ret;
        if (!m_enforceGreyScale) {
            ret =
                    m_renderer.render(convertedSrc, m_sel.getPlaneDimIndex1(), m_sel.getPlaneDimIndex2(),
                                      m_sel.getPlanePos());
        } else {
            m_greyRenderer.setNormalizationParameters(normParams[0], normParams[1]);
            ret =
                    m_greyRenderer.render(convertedSrc, m_sel.getPlaneDimIndex1(), m_sel.getPlaneDimIndex2(),
                                          m_sel.getPlanePos());
        }

        m_lastImage = ret.image();
        m_hashOfLastRendering = generateHashCode();

        return AWTImageTools.makeBuffered(ret.image());
    }

    /**
     * {@link EventListener} for {@link NormalizationParametersChgEvent} events The
     * {@link NormalizationParametersChgEvent} of the {@link AWTImageTools} will be updated
     *
     * @param normalizationParameters
     */
    @EventListener
    public void onUpdated(final NormalizationParametersChgEvent normalizationParameters) {
        m_normalizationParameters = normalizationParameters;
    }

    /**
     *
     * {@link EventListener} for {@link BundleChgEvent}. A new lookup table will be constructed using the given transfer
     * function bundle.
     *
     * @param event
     */
    @EventListener
    public void onLookupTableChgEvent(final LookupTableChgEvent<T, ARGBType> event) {
        m_lookupTable = event.getTable();
    }

    @EventListener
    public void onImageUpdated(final ImgWithMetadataChgEvent<T> e) {
        m_src = e.getRandomAccessibleInterval();

        final int size = e.getImgMetaData().getColorTableCount();
        m_colorTables = new ColorTable[size];

        for (int i = 0; i < size; i++) {
            m_colorTables[i] = e.getImgMetaData().getColorTable(i);
        }
    }

    @EventListener
    public void onImageUpdated(final ImgAndLabelingChgEvent<T, ?> e) {
        m_src = e.getRandomAccessibleInterval();
    }

    @Override
    public int generateHashCode() {
        int hash = super.generateHashCode();
        hash += m_normalizationParameters.hashCode();
        hash *= 31;
        hash += m_src.hashCode();
        hash *= 31;
        hash += m_lookupTable.hashCode();
        hash *= 31;
        hash += m_colorTables.hashCode();
        hash *= 31;
        return hash;
    }

    @Override
    public void saveAdditionalConfigurations(final ObjectOutput out) throws IOException {
        super.saveAdditionalConfigurations(out);
        m_normalizationParameters.writeExternal(out);
    }

    @Override
    public void loadAdditionalConfigurations(final ObjectInput in) throws IOException, ClassNotFoundException {
        super.loadAdditionalConfigurations(in);
        m_normalizationParameters = new NormalizationParametersChgEvent();
        m_normalizationParameters.readExternal(in);
    }

}
