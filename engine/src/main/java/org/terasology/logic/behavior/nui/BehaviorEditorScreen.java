/*
 * Copyright 2013 MovingBlocks
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.terasology.logic.behavior.nui;

import com.google.common.collect.Lists;
import org.terasology.engine.CoreRegistry;
import org.terasology.entitySystem.Component;
import org.terasology.entitySystem.entity.EntityRef;
import org.terasology.entitySystem.systems.In;
import org.terasology.logic.behavior.BehaviorNodeComponent;
import org.terasology.logic.behavior.BehaviorNodeFactory;
import org.terasology.logic.behavior.BehaviorSystem;
import org.terasology.logic.behavior.asset.BehaviorTree;
import org.terasology.logic.behavior.tree.Interpreter;
import org.terasology.rendering.nui.NUIManager;
import org.terasology.rendering.nui.UIScreenLayer;
import org.terasology.rendering.nui.UIWidget;
import org.terasology.rendering.nui.WidgetUtil;
import org.terasology.rendering.nui.databinding.Binding;
import org.terasology.rendering.nui.databinding.ReadOnlyBinding;
import org.terasology.rendering.nui.layouts.PropertyLayout;
import org.terasology.rendering.nui.properties.PropertyProvider;
import org.terasology.rendering.nui.widgets.ActivateEventListener;
import org.terasology.rendering.nui.widgets.UIDropdown;

import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.util.Arrays;
import java.util.List;

/**
 * @author synopia
 */
public class BehaviorEditorScreen extends UIScreenLayer {

    private PropertyLayout entityProperties;
    private BehaviorEditor behaviorEditor;
    private PropertyLayout properties;
    private UIDropdown<BehaviorTree> selectTree;
    private UIDropdown<Interpreter> selectEntity;
    private UIDropdown<BehaviorNodeComponent> palette;
    private BehaviorTree selectedTree;
    private Interpreter selectedInterpreter;
    private RenderableNode selectedNode;
    private BehaviorDebugger debugger;

    @In
    private NUIManager nuiManager;

    @Override
    public void initialise() {
        entityProperties = find("entity_properties", PropertyLayout.class);
        behaviorEditor = find("tree", BehaviorEditor.class);
        properties = find("properties", PropertyLayout.class);
        selectTree = find("select_tree", UIDropdown.class);
        selectEntity = find("select_entity", UIDropdown.class);
        palette = find("palette", UIDropdown.class);

        behaviorEditor.bindSelection(new Binding<RenderableNode>() {
            @Override
            public RenderableNode get() {
                return selectedNode;
            }

            @Override
            public void set(RenderableNode value) {
                selectedNode = value;
                PropertyProvider<?> provider = new PropertyProvider<>(value.getNode());
                properties.clear();
                properties.addPropertyProvider("Behavior Node", provider);
            }
        });

        selectTree.bindOptions(new Binding<List<BehaviorTree>>() {
            @Override
            public List<BehaviorTree> get() {
                return Lists.newArrayList(CoreRegistry.get(BehaviorSystem.class).getTrees());
            }

            @Override
            public void set(List<BehaviorTree> value) {
            }
        });
        selectTree.bindSelection(new Binding<BehaviorTree>() {
            @Override
            public BehaviorTree get() {
                return behaviorEditor.getTree();
            }

            @Override
            public void set(BehaviorTree value) {
                selectedTree = value;
                behaviorEditor.setTree(value);
                selectEntity.setSelection(null);
            }
        });

        selectEntity.bindOptions(new ReadOnlyBinding<List<Interpreter>>() {
            @Override
            public List<Interpreter> get() {
                BehaviorTree selection = selectTree.getSelection();
                if (selection != null) {
                    return CoreRegistry.get(BehaviorSystem.class).getInterpreter(selection);
                } else {
                    return Arrays.asList();
                }
            }
        });
        selectEntity.bindSelection(new Binding<Interpreter>() {
            @Override
            public Interpreter get() {
                return selectedInterpreter;
            }

            @Override
            public void set(Interpreter value) {
                if (selectedInterpreter != null) {
                    selectedInterpreter.setDebugger(null);
                }
                selectedInterpreter = value;
                if (selectedInterpreter != null) {
                    EntityRef minion = value.actor().minion();
                    entityProperties.clear();
                    for (Component component : minion.iterateComponents()) {
                        entityProperties.addPropertyProvider(component.getClass().getSimpleName(), new PropertyProvider<>(component));
                    }
                    debugger = new BehaviorDebugger(selectedTree);
                    selectedInterpreter.setDebugger(debugger);
                }
            }
        });

        palette.bindOptions(new ReadOnlyBinding<List<BehaviorNodeComponent>>() {
            @Override
            public List<BehaviorNodeComponent> get() {
                return Lists.newArrayList(CoreRegistry.get(BehaviorNodeFactory.class).getNodeComponents());
            }
        });

        WidgetUtil.trySubscribe(this, "create", new ActivateEventListener() {
            @Override
            public void onActivated(UIWidget button) {
                behaviorEditor.createNode(palette.getSelection());
            }
        });

        WidgetUtil.trySubscribe(this, "copy", new ActivateEventListener() {
            @Override
            public void onActivated(UIWidget button) {
                Clipboard systemClipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                String data = behaviorEditor.save();
                StringSelection contents = new StringSelection(data);
                systemClipboard.setContents(contents, contents);
            }
        });

        WidgetUtil.trySubscribe(this, "layout", new ActivateEventListener() {
            @Override
            public void onActivated(UIWidget button) {
                BehaviorTree selection = selectTree.getSelection();
                if (selection != null) {
                    selection.layout(selectedNode);
                }
            }
        });

        WidgetUtil.trySubscribe(this, "debug_run", new ActivateEventListener() {
            @Override
            public void onActivated(UIWidget button) {
                if (debugger != null) {
                    debugger.run();
                }
            }
        });
        WidgetUtil.trySubscribe(this, "debug_pause", new ActivateEventListener() {
            @Override
            public void onActivated(UIWidget button) {
                if (debugger != null) {
                    debugger.pause();
                }
            }
        });
        WidgetUtil.trySubscribe(this, "debug_reset", new ActivateEventListener() {
            @Override
            public void onActivated(UIWidget button) {
                if (selectedInterpreter != null) {
                    selectedInterpreter.reset();
                }
            }
        });
        WidgetUtil.trySubscribe(this, "debug_step", new ActivateEventListener() {
            @Override
            public void onActivated(UIWidget button) {
                if (debugger != null) {
                    debugger.step();
                }
            }
        });
    }

    @Override
    public void update(float delta) {
        CoreRegistry.get(BehaviorSystem.class).update(delta);
        super.update(delta);
    }
}
