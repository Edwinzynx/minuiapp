package tred.minos

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

class SwipeLeftGestureListener(
    context: Context,
    private val onSwipeLeft: () -> Unit
) : View.OnTouchListener {

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            if (e1 != null) {
                val diffX = e2.x - e1.x
                val diffY = e2.y - e1.y
                
                // Check if it's a horizontal swipe to the left
                if (abs(diffX) > abs(diffY) && 
                    diffX < -150 && // Minimum distance for swipe left (negative value)
                    abs(velocityX) > 200) { // Minimum velocity
                    onSwipeLeft()
                    return true
                }
            }
            return false
        }

        override fun onDown(e: MotionEvent): Boolean {
            // Return true to indicate we want to handle touch events
            return true
        }
    })

    override fun onTouch(v: View?, event: MotionEvent?): Boolean {
        return if (event != null) {
            gestureDetector.onTouchEvent(event)
        } else {
            false
        }
    }
}
