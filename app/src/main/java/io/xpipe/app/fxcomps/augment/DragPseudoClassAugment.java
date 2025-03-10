package io.xpipe.app.fxcomps.augment;

import io.xpipe.app.fxcomps.CompStructure;
import javafx.css.PseudoClass;
import javafx.scene.input.DragEvent;

public class DragPseudoClassAugment<S extends CompStructure<?>> implements Augment<S> {

    public static final PseudoClass DRAGGED_PSEUDOCLASS = PseudoClass.getPseudoClass("drag-over");

    public static <S extends CompStructure<?>> DragPseudoClassAugment<S> create() {
        return new DragPseudoClassAugment<>();
    }

    @Override
    public void augment(S struc) {
        struc.get().addEventFilter(DragEvent.DRAG_ENTERED, event -> {
            struc.get().pseudoClassStateChanged(DRAGGED_PSEUDOCLASS, true);
        });

        struc.get().addEventFilter(DragEvent.DRAG_EXITED, event -> struc.get()
                .pseudoClassStateChanged(DRAGGED_PSEUDOCLASS, false));
    }
}
