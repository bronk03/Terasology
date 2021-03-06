/*
 * Copyright 2014 MovingBlocks
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
package org.terasology.rendering.nui.internal;

import com.google.common.base.Objects;
import com.google.common.collect.Queues;
import org.reflections.ReflectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terasology.asset.AssetManager;
import org.terasology.asset.AssetType;
import org.terasology.asset.AssetUri;
import org.terasology.classMetadata.ClassLibrary;
import org.terasology.classMetadata.DefaultClassLibrary;
import org.terasology.classMetadata.copying.CopyStrategyLibrary;
import org.terasology.classMetadata.reflect.ReflectFactory;
import org.terasology.engine.CoreRegistry;
import org.terasology.engine.SimpleUri;
import org.terasology.engine.Time;
import org.terasology.engine.module.Module;
import org.terasology.engine.module.ModuleManager;
import org.terasology.entitySystem.entity.EntityRef;
import org.terasology.entitySystem.event.EventPriority;
import org.terasology.entitySystem.event.ReceiveEvent;
import org.terasology.entitySystem.systems.BaseComponentSystem;
import org.terasology.entitySystem.systems.In;
import org.terasology.input.BindButtonEvent;
import org.terasology.input.Mouse;
import org.terasology.input.events.KeyEvent;
import org.terasology.input.events.MouseButtonEvent;
import org.terasology.input.events.MouseWheelEvent;
import org.terasology.network.ClientComponent;
import org.terasology.rendering.nui.FocusManager;
import org.terasology.rendering.nui.NUIManager;
import org.terasology.rendering.nui.UIScreenLayer;
import org.terasology.rendering.nui.UIWidget;
import org.terasology.rendering.nui.asset.UIData;

import java.lang.reflect.Field;
import java.util.Deque;

/**
 * @author Immortius
 */
public class NUIManagerInternal extends BaseComponentSystem implements NUIManager, FocusManager {

    private static final Logger logger = LoggerFactory.getLogger(NUIManagerInternal.class);

    private AssetManager assetManager;

    private Deque<UIScreenLayer> screens = Queues.newArrayDeque();
    private CanvasControl canvas;
    private ClassLibrary<UIWidget> widgetsLibrary;
    private UIWidget focus;

    public NUIManagerInternal(AssetManager assetManager) {
        this.assetManager = assetManager;
        this.canvas = new CanvasImpl(this, CoreRegistry.get(Time.class), new LwjglCanvasRenderer());
    }

    public void refreshWidgetsLibrary() {
        widgetsLibrary = new DefaultClassLibrary<>(CoreRegistry.get(ReflectFactory.class), CoreRegistry.get(CopyStrategyLibrary.class));
        for (Module module : CoreRegistry.get(ModuleManager.class).getActiveCodeModules()) {
            for (Class<? extends UIWidget> elementType : module.getReflections().getSubTypesOf(UIWidget.class)) {
                widgetsLibrary.register(new SimpleUri(module.getId(), elementType.getSimpleName()), elementType);
            }
        }
    }

    @Override
    public UIScreenLayer pushScreen(AssetUri screenUri) {
        UIData data = assetManager.loadAssetData(screenUri, UIData.class);
        if (data != null && data.getRootWidget() instanceof UIScreenLayer) {
            UIScreenLayer result = (UIScreenLayer) data.getRootWidget();
            pushScreen(result);
            return result;
        }
        return null;
    }

    @Override
    public UIScreenLayer pushScreen(String screenUri) {
        AssetUri assetUri = assetManager.resolve(AssetType.UI_ELEMENT, screenUri);
        if (assetUri != null) {
            return pushScreen(assetUri);
        }
        return null;
    }

    @Override
    public <T extends UIScreenLayer> T pushScreen(AssetUri screenUri, Class<T> expectedType) {
        UIScreenLayer result = pushScreen(screenUri);
        if (expectedType.isInstance(result)) {
            return expectedType.cast(result);
        }
        return null;
    }

    @Override
    public <T extends UIScreenLayer> T pushScreen(String screenUri, Class<T> expectedType) {
        UIScreenLayer result = pushScreen(screenUri);
        if (expectedType.isInstance(result)) {
            return expectedType.cast(result);
        }
        return null;
    }

    @Override
    public void pushScreen(UIScreenLayer screen) {
        prepare(screen);
        screens.push(screen);
    }

    @Override
    public void popScreen() {
        if (!screens.isEmpty()) {
            screens.pop();
        }
    }

    @Override
    public UIScreenLayer setScreen(AssetUri screenUri) {
        UIData data = assetManager.loadAssetData(screenUri, UIData.class);
        if (data != null && data.getRootWidget() instanceof UIScreenLayer) {
            UIScreenLayer result = (UIScreenLayer) data.getRootWidget();
            setScreen(result);
            return result;
        }
        return null;
    }

    @Override
    public UIScreenLayer setScreen(String screenUri) {
        AssetUri assetUri = assetManager.resolve(AssetType.UI_ELEMENT, screenUri);
        if (assetUri != null) {
            return setScreen(assetUri);
        }
        return null;
    }

    @Override
    public <T extends UIScreenLayer> T setScreen(AssetUri screenUri, Class<T> expectedType) {
        UIScreenLayer result = setScreen(screenUri);
        if (expectedType.isInstance(result)) {
            return expectedType.cast(result);
        }
        return null;
    }

    @Override
    public <T extends UIScreenLayer> T setScreen(String screenUri, Class<T> expectedType) {
        UIScreenLayer result = setScreen(screenUri);
        if (expectedType.isInstance(result)) {
            return expectedType.cast(result);
        }
        return null;
    }

    @Override
    public void setScreen(UIScreenLayer screen) {
        screens.clear();
        prepare(screen);
        screens.push(screen);
    }

    @Override
    public void closeScreens() {
        screens.clear();
        focus = null;
    }

    public void render() {
        canvas.preRender();
        Deque<UIScreenLayer> screensToRender = Queues.newArrayDeque();
        for (UIScreenLayer layer : screens) {
            screensToRender.push(layer);
            if (!layer.isLowerLayerVisible()) {
                break;
            }
        }
        for (UIScreenLayer screen : screensToRender) {
            canvas.setSkin(screen.getSkin());
            canvas.drawWidget(screen, canvas.getRegion());
        }
        canvas.postRender();
    }

    public void update(float delta) {
        canvas.processMousePosition(Mouse.getPosition());

        for (UIScreenLayer screen : screens) {
            screen.update(delta);
        }
    }

    @Override
    public ClassLibrary<UIWidget> getWidgetMetadataLibrary() {
        return widgetsLibrary;
    }

    @Override
    public void setFocus(UIWidget widget) {
        if (!Objects.equal(widget, focus)) {
            if (focus != null) {
                focus.onLoseFocus();
            }
            focus = widget;
            if (focus != null) {
                focus.onGainFocus();
            }
        }
    }

    @Override
    public UIWidget getFocus() {
        return focus;
    }

    /*
      The following events will capture the mouse and keyboard inputs. They have high priority so the GUI will
      have first pick of input
    */

    //mouse button events
    @ReceiveEvent(components = ClientComponent.class, priority = EventPriority.PRIORITY_HIGH)
    public void mouseButtonEvent(MouseButtonEvent event, EntityRef entity) {
        if (focus != null) {
            focus.onMouseButtonEvent(event);
            if (event.isConsumed()) {
                return;
            }
        }
        if (event.isDown()) {
            if (canvas.processMouseClick(event.getButton(), Mouse.getPosition())) {
                event.consume();
            }
        } else {
            if (canvas.processMouseRelease(event.getButton(), Mouse.getPosition())) {
                event.consume();
            }
        }
    }

    //mouse wheel events
    @ReceiveEvent(components = ClientComponent.class, priority = EventPriority.PRIORITY_HIGH)
    public void mouseWheelEvent(MouseWheelEvent event, EntityRef entity) {
        if (focus != null) {
            focus.onMouseWheelEvent(event);
            if (event.isConsumed()) {
                return;
            }
            if (canvas.processMouseWheel(event.getWheelTurns(), Mouse.getPosition())) {
                event.consume();
            }
        }
    }

    //raw input events
    @ReceiveEvent(components = ClientComponent.class, priority = EventPriority.PRIORITY_HIGH)
    public void keyEvent(KeyEvent event, EntityRef entity) {
        if (focus != null) {
            focus.onKeyEvent(event);
        }
    }

    //bind input events (will be send after raw input events, if a bind button was pressed and the raw input event hasn't consumed the event)
    @ReceiveEvent(components = ClientComponent.class, priority = EventPriority.PRIORITY_HIGH)
    public void bindEvent(BindButtonEvent event, EntityRef entity) {
    }

    private void prepare(UIScreenLayer screen) {
        inject(screen);
        screen.setManager(this);
        screen.getContents();
        screen.initialise();
    }

    private void inject(Object object) {
        for (Field field : ReflectionUtils.getAllFields(object.getClass(), ReflectionUtils.withAnnotation(In.class))) {
            Object value = CoreRegistry.get(field.getType());
            if (value != null) {
                try {
                    field.setAccessible(true);
                    field.set(object, value);
                } catch (IllegalAccessException e) {
                    logger.error("Failed to inject value {} into field {} of {}", value, field, object, e);
                }
            }
        }
    }

}
