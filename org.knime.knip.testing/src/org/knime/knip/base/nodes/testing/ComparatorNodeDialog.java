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
 * --------------------------------------------------------------------- *
 *
 */
package org.knime.knip.base.nodes.testing;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

import org.knime.core.data.DataValue;
import org.knime.core.node.NodeDialog;
import org.knime.core.node.defaultnodesettings.DefaultNodeSettingsPane;
import org.knime.core.node.defaultnodesettings.DialogComponent;
import org.knime.core.node.defaultnodesettings.DialogComponentColumnNameSelection;

/**
 * {@link NodeDialog} for {@link ComparatorNodeModel}
 *
 * @author <a href="mailto:dietzc85@googlemail.com">Christian Dietz</a>
 * @author <a href="mailto:horn_martin@gmx.de">Martin Horn</a>
 * @author <a href="mailto:michael.zinsmaier@googlemail.com">Michael Zinsmaier</a>
 *
 * @param <VIN1>
 * @param <VIN2>
 */
public abstract class ComparatorNodeDialog<VIN1 extends DataValue, VIN2 extends DataValue> extends
        DefaultNodeSettingsPane {

    /**
     * Default Constructor adding all required {@link DialogComponent}s
     */
    @SuppressWarnings("unchecked")
    public ComparatorNodeDialog() {

        final Class<? extends DataValue>[] argTypeClasses = getTypeArgumentClasses();

        createNewGroup("Column selection");

        addDialogComponent(new DialogComponentColumnNameSelection(ComparatorNodeModel.createFirstColModel(),
                "first Column", 0, argTypeClasses[0]));
        addDialogComponent(new DialogComponentColumnNameSelection(ComparatorNodeModel.createSecondColModel(),
                "second Column", 0, argTypeClasses[1]));

    }

    /**
     * Retrieves the classes of the type arguments VIN1, VIN2
     */
    @SuppressWarnings("unchecked")
    private Class<? extends DataValue>[] getTypeArgumentClasses() {

        Class<?> c = getClass();
        final Class<? extends DataValue>[] res = new Class[2];
        for (int i = 0; i < 5; i++) {
            if (c.getSuperclass().equals(ComparatorNodeDialog.class)) {
                final Type[] types = ((ParameterizedType)c.getGenericSuperclass()).getActualTypeArguments();
                if (types[0] instanceof ParameterizedType) {
                    types[0] = ((ParameterizedType)types[0]).getRawType();
                }
                if (types[1] instanceof ParameterizedType) {
                    types[1] = ((ParameterizedType)types[1]).getRawType();
                }

                res[0] = (Class<VIN1>)types[0];
                res[1] = (Class<VIN2>)types[1];
                break;
            }
            c = c.getSuperclass();
        }
        return res;
    }
}
