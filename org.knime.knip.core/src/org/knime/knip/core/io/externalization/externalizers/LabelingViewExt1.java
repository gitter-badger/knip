/*
 * ------------------------------------------------------------------------
 *
 *  Copyright (C) 2003 - 2014
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
 * Created on Jul 2, 2014 by dietzc
 */
package org.knime.knip.core.io.externalization.externalizers;

import net.imglib2.img.Img;
import net.imglib2.img.ImgView;
import net.imglib2.roi.labeling.ImgLabeling;
import net.imglib2.roi.labeling.LabelingType;

import org.knime.knip.core.io.externalization.BufferedDataInputStream;
import org.knime.knip.core.io.externalization.BufferedDataOutputStream;
import org.knime.knip.core.io.externalization.Externalizer;
import org.knime.knip.core.io.externalization.ExternalizerManager;
import org.knime.knip.core.labeling.LabelingView;

/**
 *
 * @author dietzc
 */
@Deprecated
public class LabelingViewExt1 implements Externalizer<LabelingView> {

    /**
     * {@inheritDoc}
     */
    @Override
    public String getId() {
        return this.getClass().getSimpleName();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Class<? extends LabelingView> getType() {
        return LabelingView.class;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getPriority() {
        return 1;
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    @Override
    public LabelingView read(final BufferedDataInputStream in) throws Exception {
        Object read = ExternalizerManager.read(in);

        final ImgLabeling lab;
        if(read instanceof ImgLabeling){
            lab = (ImgLabeling)read;
        }else if(read instanceof Img){
            lab = new ImgLabeling<>((Img)read);
            ImgLabelingExt0.readMapping(in, lab.getMapping());
        }else{
            throw new IllegalArgumentException("Shouldn't happen at all");
        }
        return new LabelingView(lab, null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void write(final BufferedDataOutputStream out, final LabelingView obj) throws Exception {
        if (obj.getDelegate() instanceof ImgLabeling) {
            ExternalizerManager.write(out, obj.getDelegate());
        } else {
            final Img<LabelingType<?>> img;
            if (obj.getDelegate() instanceof Img) {
               img = (Img<LabelingType<?>>)obj.getDelegate();
            }else{
                img = new ImgView<LabelingType<?>>(obj.getDelegate(), obj.getFac());
            }
            ExternalizerManager.write(out, img);
            ImgLabelingExt0.writeMapping(out, img.firstElement().getMapping());
        }
    }
}
