/*******************************************************************************
 *  Copyright (c) 2012 VMware, Inc.
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  https://www.eclipse.org/legal/epl-v10.html
 *
 *  Contributors:
 *      VMware, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.config.ui.editors.integration.graph.parts;

import org.eclipse.draw2d.IFigure;
import org.eclipse.draw2d.Label;
import org.springframework.ide.eclipse.config.ui.editors.integration.graph.IntegrationImages;
import org.springframework.ide.eclipse.config.ui.editors.integration.graph.model.DelayerModelElement;


/**
 * @author Leo Dos Santos
 */
public class DelayerGraphicalEditPart extends BorderedIntegrationPart {

	public DelayerGraphicalEditPart(DelayerModelElement delayer) {
		super(delayer);
	}

	@Override
	protected IFigure createFigure() {
		Label l = (Label) super.createFigure();
		l.setIcon(IntegrationImages.getImageWithBadge(IntegrationImages.DELAYER, IntegrationImages.BADGE_SI));
		return l;
	}

	@Override
	public DelayerModelElement getModelElement() {
		return (DelayerModelElement) getModel();
	}

}
