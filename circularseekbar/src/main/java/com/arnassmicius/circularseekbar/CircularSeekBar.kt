package com.arnassmicius.circularseekbar

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.support.v4.content.ContextCompat
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View


interface OnCircularSeekBarChangeListener {
    fun onPointsChanged(circularSeekBar: CircularSeekBar, points: Int, fromUser: Boolean)
    fun onStartTrackingTouch(circularSeekBar: CircularSeekBar)
    fun onStopTrackingTouch(circularSeekBar: CircularSeekBar)
}

class CircularSeekBar @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        const val INVALID_VALUE = -1
        const val MAX = 100
        const val MIN = 0
        const val ANGLE_OFFSET = -90f
    }

    val density = resources.displayMetrics.density

    var arcColor = ContextCompat.getColor(context, R.color.color_arc)
    var progressColor = ContextCompat.getColor(context, R.color.color_progress)
    var mTextColor = ContextCompat.getColor(context, R.color.color_text)
    var mProgressWidth = (12 * density).toInt()
    var mArcWidth = (12 * density)
    var mTextSize = (72 * density)
    var mPoints: Int = MIN
    var mMin: Int = MIN
    var mMax: Int = MAX
    var mStep: Int = 10
    var mClockwise = true
    var mEnabled = true

    private var mTranslateX: Int? = null
    private var mtranslateY: Int? = null
    private var mArcRadius = 0
    private val mArcRect = RectF()

    private val mArcPaint = Paint()

    private val mProgressPaint = Paint()
    private var mProgressSweep = 0f

    private val mTextPaint = Paint()
    private val mTextRect = Rect()

    private var mUpdateTimes = 0
    private var mPreviousProgress = -1
    private var mCurrentProgress = 0f

    private var isMax = false
    private var isMin = false

    private var mIndicatorIconX: Int? = null
    private var mIndicatorIconY: Int? = null

    private var mTouchAngle: Double? = null
    private var mOnCircularSeekBarChangeListener: OnCircularSeekBarChangeListener? = null

    var mIndicatorIcon = ContextCompat.getDrawable(context, R.drawable.indicator)

    init {
        if (attrs != null) {
            val attributeArray = context.obtainStyledAttributes(attrs, R.styleable.CircularSeekBar, 0, 0)

            val indicatorIcon = attributeArray.getDrawable(R.styleable.CircularSeekBar_indicatorIcon)
            if (indicatorIcon != null) {
                mIndicatorIcon = indicatorIcon
            }

            val indicatorIconHalfWidth = mIndicatorIcon?.intrinsicWidth?.div(2)
            val indicatorIconHalfHeight = mIndicatorIcon?.intrinsicHeight?.div(2)
            mIndicatorIcon?.setBounds(
                    -indicatorIconHalfWidth!!,
                    -indicatorIconHalfHeight!!,
                    indicatorIconHalfWidth,
                    indicatorIconHalfHeight
                    )
            mPoints = attributeArray.getInt(R.styleable.CircularSeekBar_points, mPoints)
            mMin = attributeArray.getInt(R.styleable.CircularSeekBar_min, mMin)
            mMax = attributeArray.getInt(R.styleable.CircularSeekBar_max, mMax)
            mStep = attributeArray.getInt(R.styleable.CircularSeekBar_step, mStep)

            mProgressWidth = attributeArray.getDimension(R.styleable.CircularSeekBar_progressWidth, mArcWidth.toFloat()).toInt()
            progressColor = attributeArray.getColor(R.styleable.CircularSeekBar_progressColor, arcColor)

            mArcWidth = attributeArray.getDimension(R.styleable.CircularSeekBar_arcWidth, mArcWidth.toFloat())
            arcColor = attributeArray.getColor(R.styleable.CircularSeekBar_arcColor, arcColor)

            mTextSize = attributeArray.getDimension(R.styleable.CircularSeekBar_textSize, mTextSize)
            mTextColor = attributeArray.getColor(R.styleable.CircularSeekBar_textColor, mTextColor)

            mClockwise = attributeArray.getBoolean(R.styleable.CircularSeekBar_clockwise, mClockwise)
            mEnabled = attributeArray.getBoolean(R.styleable.CircularSeekBar_enabled, mEnabled)
            attributeArray.recycle()
        }

        mPoints = if (mPoints > mMax) mMax else mPoints
        mPoints = if (mPoints < mMin) mMin else mPoints

        mProgressSweep = mPoints / valuePerDegree()

        with(mArcPaint) {
            color = arcColor
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeWidth = mArcWidth
        }

        with(mProgressPaint) {
            color = progressColor
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeWidth = mProgressWidth.toFloat()
        }

        with(mTextPaint) {
            color = mTextColor
            isAntiAlias = true
            style = Paint.Style.FILL
            textSize = mTextSize
        }


    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = getDefaultSize(suggestedMinimumWidth, widthMeasureSpec)
        val height = getDefaultSize(suggestedMinimumHeight, heightMeasureSpec)
        val min = Math.min(width, height)

        mTranslateX = (width * 0.5f).toInt()
        mtranslateY = (height * 0.5f).toInt()

        val arcDiameter = min - paddingLeft
        mArcRadius = arcDiameter / 2
        val top = height / 2 - (arcDiameter / 2)
        val left = width / 2 - (arcDiameter / 2)
        mArcRect.set(left.toFloat(),
                top.toFloat(),
                left + arcDiameter.toFloat(),
                top + arcDiameter.toFloat())

        updateIndicatorIconPosition()
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun onDraw(canvas: Canvas?) {
        if (!mClockwise) {
            canvas?.scale(-1f, 1f, mArcRect.centerX(), mArcRect.centerY())
        }

        mTextPaint.getTextBounds(mPoints.toString(), 0, mPoints.toString().length, mTextRect)

        val xPos = canvas!!.width / 2 - mTextRect.width() / 2
        val yPos = ((mArcRect.centerY()) - (mTextPaint.descent() + mTextPaint.ascent()) / 2).toInt()
        canvas.drawText(mPoints.toString(), xPos.toFloat(), yPos.toFloat(), mTextPaint)

        canvas.drawArc(mArcRect, ANGLE_OFFSET, 360f, false, mArcPaint)
        canvas.drawArc(mArcRect, ANGLE_OFFSET, mProgressSweep, false, mProgressPaint)

        if (mEnabled) {
            canvas.translate((mTranslateX!! - mIndicatorIconX!!).toFloat(), (mtranslateY!! - mIndicatorIconY!!).toFloat())
            mIndicatorIcon?.draw(canvas)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (mEnabled) {
            parent.requestDisallowInterceptTouchEvent(true)

            when (event.action) {
                MotionEvent.ACTION_DOWN -> mOnCircularSeekBarChangeListener?.onStopTrackingTouch(this)
                MotionEvent.ACTION_MOVE -> updateOnTouch(event)
                MotionEvent.ACTION_UP -> {
                    mOnCircularSeekBarChangeListener?.onStopTrackingTouch(this)
                    isPressed = false
                    parent.requestDisallowInterceptTouchEvent(false)
                }
                MotionEvent.ACTION_CANCEL -> {
                    mOnCircularSeekBarChangeListener?.onStopTrackingTouch(this)
                    isPressed = false
                    parent.requestDisallowInterceptTouchEvent(false)
                }
            }
            return true
        }
        return false
    }

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        if (mIndicatorIcon != null && mIndicatorIcon!!.isStateful) {
            val state = drawableState
            mIndicatorIcon?.setState(state)
        }
        invalidate()
    }

    private fun updateOnTouch(event: MotionEvent) {
        isPressed = true
        mTouchAngle = convertTouchEventPointToAngle(event.x, event.y)
        val progress = convertAngleToProgress(mTouchAngle!!)
        updateProgress(progress, true)
    }

    private fun updateProgress(progress: Int, fromUser: Boolean) {
        var progress = progress

        // detect points change closed to max or min
        val maxDetectValue = (mMax.toDouble() * 0.95).toInt()
        val minDetectValue = (mMax.toDouble() * 0.05).toInt() + mMin
        //		System.out.printf("(%d, %d) / (%d, %d)\n", mMax, mMin, maxDetectValue, minDetectValue);

        mUpdateTimes++
        if (progress == INVALID_VALUE) {
            return
        }

        // avoid accidentally touch to become max from original point
        // 避免在靠近原點點到直接變成最大值
        if (progress > maxDetectValue && mPreviousProgress == INVALID_VALUE) {
            //			System.out.printf("Skip (%d) %.0f -> %.0f %s\n",
            //					progress, mPreviousProgress, mCurrentProgress, isMax ? "Max" : "");
            return
        }


        // record previous and current progress change
        // 紀錄目前和前一個進度變化
        if (mUpdateTimes == 1) {
            mCurrentProgress = progress.toFloat()
        } else {
            mPreviousProgress = mCurrentProgress.toInt()
            mCurrentProgress = progress.toFloat()
        }

        //		if (mPreviousProgress != mCurrentProgress)
        //			System.out.printf("Progress (%d)(%f) %.0f -> %.0f (%s, %s)\n",
        //					progress, mTouchAngle,
        //					mPreviousProgress, mCurrentProgress,
        //					isMax ? "Max" : "",
        //					isMin ? "Min" : "");

        // 不能直接拿progress來做step
        mPoints = progress - progress % mStep

        /**
         * Determine whether reach max or min to lock point update event.
         *
         * When reaching max, the progress will drop from max (or maxDetectPoints ~ max
         * to min (or min ~ minDetectPoints) and vice versa.
         *
         * If reach max or min, stop increasing / decreasing to avoid exceeding the max / min.
         */
        // 判斷超過最大值或最小值，最大最小值不重複判斷
        // 用數值範圍判斷預防轉太快直接略過最大最小值。
        // progress變化可能從98 -> 0/1 or 0/1 -> 98/97，而不會過0或100
        if (mUpdateTimes > 1 && !isMin && !isMax) {
            if (mPreviousProgress >= maxDetectValue && mCurrentProgress <= minDetectValue &&
                    mPreviousProgress > mCurrentProgress) {
                isMax = true
                progress = mMax
                mPoints = mMax
                //				System.out.println("Reach Max " + progress);
                if (mOnCircularSeekBarChangeListener != null) {
                    mOnCircularSeekBarChangeListener?.onPointsChanged(this, progress, fromUser)
                    return
                }
            } else if ((mCurrentProgress >= maxDetectValue
                            && mPreviousProgress <= minDetectValue
                            && mCurrentProgress > mPreviousProgress) || mCurrentProgress <= mMin) {
                isMin = true
                progress = mMin
                mPoints = mMin
                //				Log.d("Reach", "Reach Min " + progress);
                if (mOnCircularSeekBarChangeListener != null) {
                    mOnCircularSeekBarChangeListener?.onPointsChanged(this, progress, fromUser)
                    return
                }
            }
            invalidate()
        } else {

            // Detect whether decreasing from max or increasing from min, to unlock the update event.
            // Make sure to check in detect range only.
            if (isMax and (mCurrentProgress < mPreviousProgress) && mCurrentProgress >= maxDetectValue) {
                //				System.out.println("Unlock max");
                isMax = false
            }
            if (isMin
                    && mPreviousProgress < mCurrentProgress
                    && mPreviousProgress <= minDetectValue && mCurrentProgress <= minDetectValue
                    && mPoints >= mMin) {
                //				Log.d("Unlock", String.format("Unlock min %.0f, %.0f\n", mPreviousProgress, mCurrentProgress));
                isMin = false
            }
        }

        if (!isMax && !isMin) {
            progress = if (progress > mMax) mMax else progress
            progress = if (progress < mMin) mMin else progress

            if (mOnCircularSeekBarChangeListener != null) {
                progress = progress - progress % mStep

                mOnCircularSeekBarChangeListener?.onPointsChanged(this, progress, fromUser)
            }

            mProgressSweep = progress.toFloat() / valuePerDegree()
            //			if (mPreviousProgress != mCurrentProgress)
            //				System.out.printf("-- %d, %d, %f\n", progress, mPoints, mProgressSweep);
            updateIndicatorIconPosition()
            invalidate()
        }
    }
    private fun convertAngleToProgress(angle: Double): Int {
        return Math.round(valuePerDegree() * angle).toInt()
    }

    private fun convertTouchEventPointToAngle(xPos: Float, yPos: Float): Double {
        var x = xPos - mTranslateX!!
        val y = yPos - mtranslateY!!

        x = if (mClockwise) x else -x
        var angle = Math.toDegrees(Math.atan2(y.toDouble(), x.toDouble()) + (Math.PI / 2))
        angle = if (angle < 0) (angle + 360) else angle
        return angle
    }

    private fun valuePerDegree(): Float {
        return mMax / 360f
    }

    private fun updateIndicatorIconPosition() {
        val thumbAngle = mProgressSweep + 90
        mIndicatorIconX = (mArcRadius * Math.cos(Math.toRadians(thumbAngle.toDouble()))).toInt()
        mIndicatorIconY = (mArcRadius * Math.sin(Math.toRadians(thumbAngle.toDouble()))).toInt()
    }
}
        // https://medium.com/dualcores-studio/make-an-android-custom-view-publish-and-open-source-99a3d86df228
        // https://github.com/enginebai/SwagPoints
