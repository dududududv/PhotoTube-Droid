package com.yunai.phototube.ui

import androidx.compose.ui.unit.*
import org.junit.Assert.*
import org.junit.Test

class GlassMenuPlacementTest {
    private val window=IntSize(400,800)
    private val menu=IntSize(280,200)
    @Test fun topRightGrowsFromTriggerInsteadOfExtremeCorner() {
        val p=glassMenuPlacement(IntRect(336,20,384,68),window,LayoutDirection.Ltr,menu)
        assertEquals(IntOffset(104,68),p.offset)
        assertEquals(256f/280,p.origin.pivotFractionX,.001f)
        assertEquals(0f,p.origin.pivotFractionY,0f)
    }
    @Test fun rtlMirrorsTriggerAndAnimationOrigin() {
        val p=glassMenuPlacement(IntRect(16,20,64,68),window,LayoutDirection.Rtl,menu)
        assertEquals(IntOffset(16,68),p.offset)
        assertEquals(24f/280,p.origin.pivotFractionX,.001f)
        assertEquals(0f,p.origin.pivotFractionY,0f)
    }
    @Test fun bottomTriggerPlacesMenuAboveAndGrowsFromBottom() {
        val p=glassMenuPlacement(IntRect(336,720,384,768),window,LayoutDirection.Ltr,menu)
        assertEquals(IntOffset(104,520),p.offset)
        assertEquals(1f,p.origin.pivotFractionY,0f)
    }
    @Test fun clampedMenuKeepsOriginBesideTrigger() {
        val p=glassMenuPlacement(IntRect(0,350,48,398),window,LayoutDirection.Ltr,IntSize(280,700))
        assertEquals(IntOffset(0,100),p.offset)
        assertEquals(24f/280,p.origin.pivotFractionX,.001f)
        assertEquals(274f/700,p.origin.pivotFractionY,.001f)
    }
}
