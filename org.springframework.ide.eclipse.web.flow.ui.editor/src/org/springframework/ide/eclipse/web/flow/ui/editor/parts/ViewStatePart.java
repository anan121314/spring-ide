/*
 * Copyright 2002-2005 the original author or authors.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package org.springframework.ide.eclipse.web.flow.ui.editor.parts;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.eclipse.draw2d.geometry.Dimension;
import org.eclipse.draw2d.geometry.Rectangle;
import org.eclipse.draw2d.graph.CompoundDirectedGraph;
import org.eclipse.draw2d.graph.Edge;
import org.eclipse.draw2d.graph.Node;
import org.springframework.ide.eclipse.web.flow.core.model.IAction;
import org.springframework.ide.eclipse.web.flow.core.model.ISubFlowState;
import org.springframework.ide.eclipse.web.flow.core.model.IViewState;

public class ViewStatePart extends ChildrenStatePart {

    protected void applyChildrenResults(CompoundDirectedGraph graph, Map map) {
        for (int i = 0; i < getChildren().size(); i++) {
            AbstractStatePart part = (AbstractStatePart) getChildren().get(i);
            //part.applyGraphResults(graph, map);
            Node n = (Node) map.get(part);
            Dimension dim = part.getFigure().getPreferredSize();
            part.getFigure().setBounds(
                    new Rectangle(n.x, n.y, n.width, dim.height));
        }
    }

    public void contributeEdgesToGraph(CompoundDirectedGraph graph, Map map) {
        List outgoing = getSourceConnections();
        for (int i = 0; i < outgoing.size(); i++) {
            StateTransitionPart part = (StateTransitionPart) getSourceConnections()
                    .get(i);
            part.contributeToGraph(graph, map);
        }
        for (int i = 0; i < getChildren().size(); i++) {
            AbstractStatePart child = (AbstractStatePart) children.get(i);

            if (child.getModel() instanceof IAction) {
                if (i + 1 < children.size()) {
                    // insert dummy edges
                    Edge e = new Edge((Node) map.get(child), (Node) map
                            .get(getChildren().get(i + 1)));
                    e.weight = 1;
                    graph.edges.add(e);
                    map.put(this.toString() + i, e);
                }
            }
            else {
                child.contributeEdgesToGraph(graph, map);
            }
        }
    }

    protected List getModelChildren() {
        if (getModel() instanceof IViewState) {
            List child = new ArrayList();
            if (((IViewState) getModel()).getSetup() != null) {
                child.add(((IViewState) getModel()).getSetup());
            }
            return child;
        }
        else {
            return Collections.EMPTY_LIST;
        }
    }
}