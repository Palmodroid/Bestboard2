package org.lattilad.bestboard;


import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Handler;
import android.view.InflateException;
import android.view.MotionEvent;
import android.view.View;

import org.lattilad.bestboard.buttons.Button;
import org.lattilad.bestboard.buttons.ButtonMainTouch;
import org.lattilad.bestboard.buttons.ButtonMultiTouch;
import org.lattilad.bestboard.debug.Debug;
import org.lattilad.bestboard.scribe.Scribe;
import org.lattilad.bestboard.states.MetaState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.lattilad.bestboard.scribe.Scribe.debug;

public class LayoutView extends View
    {
    /**
     ** CLASS VARIABLES
     **/

    /**
     * Data of actual layout
     */
    private Layout layout = null;

    /**
     * screen width is stored to check whether new measurement is needed
     * rotation will change it
     * requestLayout will invalidate it
     * screen-orientation ??
     */
    private int screenWidthInPixels = -1;
    private int screenHeightInPixels = -1;
    private int calculatedHeightInPixels = -1;

    /**
     ** CONSTRUCTION OF THE LAYOUT
     ** Parsing phase (Layout):
     **   1. Constructor - adds non screen-specific data
     **   2. addButton - populates the buttons array and
     **      setShift - sets the shift levels by descriptor file
     ** Displaying phase (LayoutView):
     **   3. onMeasure - receives screen diameters
     **   4. calculateScreenData - screen specific information set by onMeasure
     **/

    /**
     * Originally standard View constructors were used,
     * Now LayoutView can only be started from code, with its own constructor
     */
    public LayoutView(Context context)
        {
        super(context);
        Scribe.locus(Debug.VIEW);

        strokePaint = new Paint();
        strokePaint.setStyle(Paint.Style.FILL);
        //strokePaint.setColor(data.strokeColor);

        pathPaint = new Paint();
        pathPaint.setStyle(Paint.Style.STROKE);
        pathPaint.setStrokeWidth( 5f );
        }

    /**
     * Attached Layout data is needed right after construction.
     * First set is NOT checked!
     * It can be changed later
     * If size is changed, then requestLayout is called
     * If size is the same, then only invalidate() is needed
     */
    public void setLayout(Layout layout)
        {
        Scribe.locus( Debug.VIEW );

        // same layout can be at several use levels
        // orientation change will cancel touches
        if ( this.layout != layout)
            {
            debug( Debug.VIEW, "Layout is about to set: " + layout.toString() );
            if ( this.layout != null )
                {
                debug(Debug.VIEW, "Layout request is called");

                // any forced meta-state should be reverted
                this.layout.revertForcedMetaStates();

                requestLayout(); // it should be called later
                }

            this.layout = layout;

            // mainTouchBow cannot be null, but it cannot get value before setLayout
            // !! THIS SHOULD BE ORGANISED ON AN OTHER WAY !!
            if ( mainTouchBow == null )
                {
                mainTouchBow = new MainTouchBow();
                }

            layout.forceMetaStates();

            /**
             * Multi touches connect to their layout.
             * If layout is changed, then these touches cannot start a new MAIN touch,
             * even not if main touch is empty.
             * The only possible action is ACTION_UP (or CANCEL) which release the button (on its layout).
             * This method will disable active multi-touches
             * !! CHANGED !!
             */

            for (MultiTouchBow multiTouchBow : multiTouchPointers.values())
                {
                multiTouchBow.touchCode = Layout.EMPTY_TOUCH_CODE;
                }

            // main touch
            // _touchEventUp();
            // evaluateMain(TOUCH_UP, null);
            mainTouchBow = new MainTouchBow(); //mainTouchBow() becomes invalid
                
            // if change happens during evaluation, evaluation should be stopped
            pointerChangeFlag = BOARD_CHANGE;

            strokePaint.setColor( layout.softBoardData.strokeColor );
            pathPaint.setColor( layout.softBoardData.strokeColor );

            // clears strokePaths
            strokePaths.clear();

            }
        // !!!!!!! SIZE CONTROL IS STILL MISSING !!!!!!!!!

        }

    @Override
    public void requestLayout()
        {
        Scribe.locus( Debug.VIEW );

        // layout-change needs recalculation
        screenHeightInPixels = -1;
        screenWidthInPixels = -1;

        super.requestLayout();
        }


    boolean fakeView = false;

    /**
     ** There are two serious system errors here:
     ** 1. Because of Navigation Bar measurement, after the first onMeasurement cycle
     ** the TOP position is sometimes bad. This only occurs after orientation change
     ** 2. After entering TEST MODE, there is an onMeasure cycle with false height values.
     ** There is immiadetely a new cycle - with the right values -, but onDraw is NOT
     ** started after the second cycle!
     **
     ** 1. Called by SoftBoardProcessor.onCreateInputView() (after orientation change eg.)
     ** 2. Called by constructor of SoftBoardProcessor
     **
     ** Can be easier to call it from every setLayout??
     **/
    public void measureFakeViewFirst()
        {
        fakeView = true;
        calculatedHeightInPixels = -1;
        }


    /**
     * Parent is a FrameView with "match_parent" width and "wrap_content" height.
     * Therefore the parameters are: EXACTLY screen_width and AT_MOST screen_height values.
     * There are two (several?) cycles of measuring. Calculation is performed during the first cycle,
     * after that layoutHeightInPixels will be set, and the second calculations are skipped.
     * Screen width is stored in screenWidthInPixels, so new calculations with this with are skipped.
     */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) throws InflateException
        {
        Scribe.locus(Debug.VIEW);

        // Check mode first. Only Width EXACTLY and Height AT_MOST are allowed!
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);

        // ?? Can't call it with exact height in the second run?
        if (widthMode != MeasureSpec.EXACTLY || heightMode != MeasureSpec.AT_MOST)
            {
            Scribe.error("Measure modes are incompatible with LayoutView!");
            throw new InflateException("Measure modes are incompatible with LayoutView!");
            }

        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        // onCreateInputView signs, that there was an Orientation Change
        // If there is a Navigation Bar, then measure cycle seriously fails after orientation change
        // So fake values are set in this round, and onDraw will call requestLayout() to set the
        // correct values
        if ( fakeView )
            {
            debug( Debug.VIEW, "onMeasure values: " +
                    "width: " + widthSize +
                    ", height: " + heightSize +
                    "; onMeasure is measuring fake view!");

            // Animation is nicer, if fakeView is smaller than keyboardView
            // BUT!
            // height can be lower during measuring cycle, than previously returned height!
            // so returning height of ex. 1 px could result a view with 0 px height!
            // if height is 0, than onDraw (and requestLAyout) is NOT called!
            // 25% height seems to be a good solution

            if ( calculatedHeightInPixels < 0 )
                calculatedHeightInPixels = heightSize / 4;
            setMeasuredDimension(widthSize, calculatedHeightInPixels);
            return;
            }

        // NORMAL MEASURING CYCLE STARTS HERE

        // Width is AT_MOST, so it can be even the calculated height (even smaller)
        if ( heightSize != screenHeightInPixels && heightSize > calculatedHeightInPixels )
            {
            screenHeightInPixels = heightSize;
            calculatedHeightInPixels = -1;
            }

        // Width is EXACT, so new calculation is needed
        if ( widthSize != screenWidthInPixels )
            {
            screenWidthInPixels = widthSize;
            calculatedHeightInPixels = -1;
            }

        // Width or Height was changed, calculation is missing
        if (calculatedHeightInPixels < 0)
            {
            // layout dimensions are calculated (and stored) here
            layout.calculateScreenData(screenWidthInPixels, screenHeightInPixels);
            calculatedHeightInPixels = layout.areaHeightInPixels;
            }

        debug( Debug.VIEW, "onMeasure values: " +
                "width: " + widthSize +
                ", height: " + heightSize +
                ", screen-width: " + screenWidthInPixels +
                ", screen-height: " + screenHeightInPixels +
                ", calculated height: " + calculatedHeightInPixels );

        setMeasuredDimension( screenWidthInPixels, calculatedHeightInPixels );
        }


    /**
     ** CLASS VARIABLES FOR TOUCHES
     **/

    private final static int TOUCH_DOWN = 0;
    private final static int TOUCH_MOVE = 1;
    private final static int TOUCH_HOLD = 2;
    private final static int TOUCH_UP = 3;

    public static final long NEVER = Long.MAX_VALUE;


    // STROKE - MAIN touch curve between touch-down and touch-up

    /**
     * Inner class to store point coordinates for stroke
     */
    private class StrokePoint
        {
        protected StrokePoint(int canvasX, int canvasY)
            {
            this.canvasX = canvasX;
            this.canvasY = canvasY;
            }

        int canvasX;
        int canvasY;
        }

    /**
     * PointerId for the stroke and mainTouchBow
     * -1: no active bow/stroke, new stroke can start
     */
    private int strokePointerId = -1;

    /**
     * Touched coordinates are stored during touch events, and will be displayed later
     */
    List<StrokePoint> strokePoints = new ArrayList<StrokePoint>();

    List<Path> strokePaths = new ArrayList<Path>();
    Path lastStrokePath = null;

    /**
     * Paint for stroke
     */
    private Paint strokePaint;
    private Paint pathPaint;

    // MAIN TOUCH - ButtonMainTouch subclasses can work only with a single touch
    // Multiple touches could be active, but only one MAIN Touch can exist at once
    // BOW - part of the stroke touching one button (touchCode)
    // It can be repetitive OR long/press sensitive, but NOT BOTH!

    private class MainTouchBow
        {
        // Elevated (empty) main touch
        MainTouchBow()
            {
            this( Layout.EMPTY_TOUCH_CODE, null );
            }

        MainTouchBow(int touchCode, ButtonMainTouch buttonMainTouch)
            {
            this.touchCode = touchCode;
            this.buttonMainTouch = buttonMainTouch;

            // clear previous repeats
            repeatHandler.removeCallbacks( repeatRunnable );

            if ( buttonMainTouch != null )
                {
                debug( Debug.TOUCH, "MainTouchBow created.");
                // in light-check cursor position should be checked before a new bow
                layout.softBoardData.softBoardListener.checkAtBowStart();

                // start timing of repeats
                repeatHandler.postDelayed( repeatRunnable, layout.softBoardData.stayBowTime );
                }
            else
                {
                debug( Debug.TOUCH, "MainTouchBow with no button created.");
                layout.softBoardData.softBoardListener.checkAtStrokeEnd();
                }
            }

        // touchCode of the current bow
        final int touchCode;

        // button of the current current bow
        final ButtonMainTouch buttonMainTouch;

        // length of the bow
        int moveCounter = 0;
        // strong presses applied to this bow
        int pressureCounter = 0;

        void increaseMoveCounter()
            {
            moveCounter++;
            }

        void increasePressureCounter()
            {
            pressureCounter++;
            }

        void resetMoveAndPressureCounter()
            {
            moveCounter = 0;
            pressureCounter = 0;
            }

        boolean isLong()
            {
            return moveCounter == layout.softBoardData.longBowCount;
            }

        boolean isPressed()
            {
            return pressureCounter == layout.softBoardData.pressBowCount;
            }
        }

    /**
     * Bow of the MAIN TOUCH.
     * It cannot be null, if touch (stroke) is elevated, it have to be empty, but not null!!
     * strokePointerId == -1 means an elevated touch
     * (because empty buttons have got the same MainTouchBow; elevated is a special empty button)
     */
    private MainTouchBow mainTouchBow;


    private Handler repeatHandler = new Handler();

    private Runnable repeatRunnable = new Runnable()
        {
        @Override
        public void run()
            {
            debug( Debug.TOUCH, "REPEAT touch is detected." );
            repeatHandler.postDelayed( repeatRunnable,
                    mainTouchBow.buttonMainTouch.mainTouchSecondary( ButtonMainTouch.ON_STAY ) ?
                    layout.softBoardData.repeatTime : layout.softBoardData.stayBowTime );
            }
        };


    // MULTI TOUCH - ButtonMultiTouch subclasses can work with multiple touches
    // These touches start as MAIN touches, but then MAIN will be free

    private class MultiTouchBow
        {
        int touchCode;
        ButtonMultiTouch buttonMultiTouch;

        MultiTouchBow(int touchCode, ButtonMultiTouch buttonMultiTouch)
            {
            this.touchCode = touchCode;
            this.buttonMultiTouch = buttonMultiTouch;
            }
        }

    // MULTI TOUCH POINTERS - secondary (multitouch pointers)
    // Key (Integer) : pointerId
    // Value MetaBow : (int touchCode, ButtonMeta buttonMeta)
    private Map<Integer, MultiTouchBow> multiTouchPointers = new HashMap<>();

    // During a stroke MAIN TOUCH BOW can convert to MULTI TOUCH BOW (and vice versa)
    // In these cases further touch evaluation should be stopped;
    // This flags signs this.

    private int pointerChangeFlag;

    private static final int META_TO_MAIN_CHANGE = -1;
    private static final int NO_CHANGE = 0;
    private static final int MAIN_TO_META_CHANGE = 1;
    private static final int BOARD_CHANGE = 2;
    // MAIN_TO_META_CHANGE and BOARD_CHANGE are evaluated together, these are on the positive side


    /**
     ** TOUCH EVENTS
     **/

    /**
     * Creates the stroke from touch (down) to release (up).
     * Only one stroke is allowed, new stroke can be created only, if strokePointerId is -1.
     * strokePoints are stored.
     * bowMoveCounter and bowPressureCounter will be increased if needed,
     * and evaluateMain() will be called after each touch.
     * View will be invalidated after down/move/up, but not after hold
     */
    @Override
    public boolean onTouchEvent(MotionEvent event)
        {
        int index;
        int id;

        Scribe.locus( Debug.TOUCH_VERBOSE );
        // Scribe.debug( Debug.VIEW, this.toString() + " touchEvent " + event.getActionMasked());

        pointerChangeFlag = NO_CHANGE;

        switch (event.getActionMasked())
            {
            case MotionEvent.ACTION_DOWN:

                // Security valve: there is only THE FIRST touch now;
                // all remained (abandoned) touches should be finished first
                debug( Debug.TOUCH, "First pointer is DOWN. Checking abandoned pointers." );

                // MAIN stroke - like UP
                if (strokePointerId != -1)
                    {
                    Scribe.error("Abandoned MAIN pointer! Pointer is lifted UP:" + strokePointerId);
                    _touchEventUp();
                    }
                else
                    {
                    debug( Debug.TOUCH, "MAIN pointer is empty." );
                    }

                // META stroke - RELEASE - ?? CLEARing state should be better ??
                if (!multiTouchPointers.isEmpty())
                    {
                    for (MultiTouchBow multiTouchBow : multiTouchPointers.values())
                        {
                        Scribe.error("Abandoned META pointer! TouchCode is released:" + multiTouchBow.touchCode);
                        multiTouchBow.buttonMultiTouch.multiTouchEvent(ButtonMultiTouch.META_RELEASE);
                        }
                    multiTouchPointers.clear();

                    // META (indicator) keys change without the change of the MAIN
                    this.invalidate();

                    // MAIN clears it, but after MAIN->META change, META should clear it, too
                    strokePoints.clear();
                    }
                else
                    {
                    debug(Debug.TOUCH, "MULTI pointer array is empty.");
                    }

                // just for security use- and meta-states are checked, that no touch remained
                for (MetaState metaState : layout.softBoardData.layoutStates.metaStates)
                    metaState.checkNoTouch();
                layout.softBoardData.boardTable.checkNoTouch();

                // break is not needed here, code continues with new touch

            case MotionEvent.ACTION_POINTER_DOWN:

                // Only one new touch point, no historical values.
                debug( Debug.TOUCH, "Pointer is DOWN." );

                // bowPointerId should be empty to start new bow (there is no bow currently)
                // THIS CAN START A NEW BOW ON A PREVIOUSLY HIDDEN LAYOUT
                if (strokePointerId < 0)
                    {
                    index = event.getActionIndex(); // index of the newest touch
                    strokePointerId = event.getPointerId( index );

                    debug( Debug.TOUCH, "MAIN pointer was empty, new stroke is started. Id: " + strokePointerId );

                    // Touched StrokePoints
                    StrokePoint strokePoint = new StrokePoint((int) event.getX(index), (int) event.getY(index));

                    // Evaluation
                    evaluateMain(TOUCH_DOWN, strokePoint);
                    }
                else
                    {
                    // all touches after MAIN are omitted
                    Scribe.error("MAIN pointer already started, new pointer is omitted!");
                    }
                break;

            case MotionEvent.ACTION_MOVE:

                // onTouchEvent cannot work between delayed call of requestLayout() and onMeasure()
                // during this phase screenWidthInPixels == -1

                // !! Theoretically invalidated phase affects all motion-events !!
                // But
                // UP and CANCEL events do not need any layout-specific data
                // DOWN/POINTER_DOWN cannot happen so early !! (Is it true ??)
                // If not, an other check could be inserted between DOWN and POINTER_DOWN

                if ( screenWidthInPixels < 0 )
                    {
                    debug( Debug.TOUCH, "Layout is not ready yet, touch moves are dropped: " + event.getHistorySize()+1 );
                    return true;
                    }

                debug( Debug.TOUCH_VERBOSE, "Pointer is in HOLD/MOVE." );

                debug( Debug.TOUCH_VERBOSE, "META pointers to evaluate: " + multiTouchPointers.size());

                // Check all META pointers for movement - NO Historical values are checked
                Iterator<Map.Entry<Integer, MultiTouchBow>> iterator =
                        multiTouchPointers.entrySet().iterator();

                while (iterator.hasNext())
                    {
                    Map.Entry<Integer, MultiTouchBow> multiTouchPointer = iterator.next();

                    index = event.findPointerIndex( multiTouchPointer.getKey() );
                    if (index != -1)
                        {
                        debug(Debug.TOUCH_VERBOSE, "META pointer check: " + index);

                        int color = layout.colorFromMap((int) event.getX(index), (int) event.getY(index));
                        int newTouchCode = Layout.touchCodeFromColor(color);

                        if (newTouchCode != multiTouchPointer.getValue().touchCode)
                            {
                            // if layout was changed for this pointer,
                            // new button (touchCode) will behave, as the previous one on the previous layout
                            if (multiTouchPointer.getValue().touchCode == Layout.EMPTY_TOUCH_CODE)
                                {
                                debug(Debug.TOUCH, "META button will refer to new touchCode on new layout: " + newTouchCode);
                                multiTouchPointer.getValue().touchCode = newTouchCode;
                                }
                            // if button-center was reached
                            else if (!Layout.outerRimFromColor(color))
                                {
                                debug(Debug.TOUCH, "META pointer left its button.");
                                multiTouchPointer.getValue().buttonMultiTouch.multiTouchEvent(ButtonMultiTouch.META_RELEASE);
                                // META (indicator) keys change without the change of the MAIN
                                this.invalidate();

                                if (strokePointerId == -1)
                                    {
                                    strokePointerId = multiTouchPointer.getKey();
                                    debug(Debug.TOUCH, "META pointer changed to MAIN. Id: " + strokePointerId);
                                    // BowTouchCode == EMPTY_TOUCH_CODE; like ACTION_DOWN
                                    // BowButton == null; like ACTION_DOWN

                                    // newly started MAIN will be NOT evaluated in this turn,
                                    // it will be evaluated as MAIN, without the historical part
                                    pointerChangeFlag = META_TO_MAIN_CHANGE;
                                    } else
                                    {
                                    debug(Debug.TOUCH, "META pointer removed. MAIN is already occupied by Id: " + strokePointerId);
                                    }

                                iterator.remove();
                                }
                            }
                        }
                    else
                        {
                        // pointer disappeared without UP
                        Scribe.error("META pointer disappeared without up: "
                                + multiTouchPointer.getKey());
                        }
                    }

                // Check MAIN pointer
                debug( Debug.TOUCH_VERBOSE, "MAIN Pointer to evaluate: " + strokePointerId);

                // strokePointerId can be -1, this is not checked!!
                index = event.findPointerIndex(strokePointerId);
                if (index != -1)
                    {
                    // if touch arrives to a meta-key, pointerChangeFlag changes
                    // Evaluation of the historical values could be continued still MAIN does not change
                    for (int h = 0; h < event.getHistorySize() && pointerChangeFlag == NO_CHANGE; h++)
                        {
                        _touchEventsHoldAndMove(
                                (int) event.getHistoricalX(index, h),
                                (int) event.getHistoricalY(index, h),
                                event.getHistoricalPressure(index, h));
                        }

                    if ( pointerChangeFlag == NO_CHANGE || pointerChangeFlag == META_TO_MAIN_CHANGE )
                        {
                        _touchEventsHoldAndMove(
                                (int) event.getX(index),
                                (int) event.getY(index),
                                event.getPressure(index));
                        }
                    }
                // !! if index is -1 and strokePointerId is not -1 then pointer disapperaed without UP
                else if (strokePointerId != -1)
                    {
                    Scribe.error("MAIN disappeared without UP!");
                    }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:

                index = event.getActionIndex(); // index of the newest touch
                id = event.getPointerId(index);

                // MAIN stroke UP
                if (id == strokePointerId) // Cannot be -1
                    {
                    debug( Debug.TOUCH, "MAIN Pointer is UP: " + id );
                    _touchEventUp();
                    }

                // META stroke RELEASE
                else
                    {
                    MultiTouchBow multiTouchBow = multiTouchPointers.get(id);

                    if (multiTouchBow != null)
                        {
                        debug( Debug.VIEW, "META Pointer UP. Id: " + id + " TouchCode: " + multiTouchBow.touchCode);

                        multiTouchBow.buttonMultiTouch.multiTouchEvent(ButtonMultiTouch.META_RELEASE);
                        // META (indicator) keys change without the change of the MAIN
                        this.invalidate();

                        multiTouchPointers.remove(id);

                        // MAIN clears it, but after MAIN->META change, last META should clear it, too
                        if (strokePointerId == -1 && multiTouchPointers.isEmpty())
                            {
                            strokePoints.clear();
                            }
                        }
                    }
                break;

            case MotionEvent.ACTION_CANCEL:

                debug( Debug.VIEW, "ALL pointers CANCEL");

                // CANCEL stores more touches (all touches if SPen nears to the screen)
                // ALL MAIN and META touches are cancelled

                // MAIN stroke - like UP
                if (strokePointerId != -1)
                    {
                    _touchEventUp();
                    }

                // META stroke - CANCEL == states change to LOCK setting
                if (!multiTouchPointers.isEmpty())
                    {
                    for (MultiTouchBow multiTouchBow : multiTouchPointers.values())
                        {
                        multiTouchBow.buttonMultiTouch.multiTouchEvent(ButtonMultiTouch.META_CANCEL);
                        // META (indicator) keys change without the change of the MAIN
                        }
                    multiTouchPointers.clear();
                    // META (indicator) keys change without the change of the MAIN
                    this.invalidate();
                    // MAIN clears it, but after MAIN->META change, META should clear it, too
                    strokePoints.clear();
                    }
                break;

            }

        return true;
        }

    // Helper for onTouchEvents() - both MOVE and HOLD touches come here
    private void _touchEventsHoldAndMove(int canvasX, int canvasY, float canvasPressure)
        {
        if (canvasPressure > layout.softBoardData.pressBowThreshold && canvasPressure != 1.0f)
            {
            debug( Debug.TOUCH, " prefsPressureThreshold: " + layout.softBoardData.pressBowThreshold + ", canvasPressure: " + canvasPressure);
            mainTouchBow.increasePressureCounter();
            }

        // Scribe.debug( Debug.VIEW, "StrokePoints size: " + strokePoints.size() );
        StrokePoint strokePoint = new StrokePoint(canvasX, canvasY);
        if ( strokePoints.size() > 0 && // after setLayout() stroke can start with "move" - it will be HOLD
        		strokePoints.get(strokePoints.size() - 1).canvasX == strokePoint.canvasX &&
                strokePoints.get(strokePoints.size() - 1).canvasY == strokePoint.canvasY)
            {
            // There were no point movements - TOUCH_HOLD
            // Points are not stored, but evaluation should be called,
            // Repeat touches are checked by repeat-handler - instead of evaluateMain(TOUCH_HOLD, strokePoint);
            }
        else // point was moved
            {
            // mainTouchBow.increaseMoveCounter(); -> evaluateMain - TOUCH_MOVE part
            // It is increased only if touch touches it's own button. On the surrounding buttons move counter is skipped.
            evaluateMain(TOUCH_MOVE, strokePoint);
            }
        }


    // Helper for onTouchEvents() - UP and DOWN (if UP is missing) comes here
    private void _touchEventUp()
        {
        // mainPointerId control
        strokePointerId = -1;
        // Evaluation
        evaluateMain(TOUCH_UP, null);
        }


    /**
     * !! THIS SHOULD GO TO LAYOUT STATES !!
     * "type" will turn off meta/link ON states
     */
    public void type()
        {
        typeMetaState();
        typeLinkState();
        }

    /**
     * evaluateMain should call meta and link separately
     */
    private void typeMetaState()
        {
        // if not meta -> call meta-states, maybe they should finish
        for (MetaState metaState : layout.softBoardData.layoutStates.metaStates)
            {
            metaState.type();
            }
        }

    /**
     * evaluateMain should call meta and link separately
     */
    private void typeLinkState()
        {
        layout.softBoardData.boardTable.type();
        }


    /**
     * Each main touch arrives here.
     * Bows should start and finish here, but moving and pressing will increase bow values in onTouchEvents.
     * - bowAction: TOUCH_DOWN, TOUCH_HOLD, TOUCH_MOVE, TOUCH_UP
     * - strokePoint: coordinates of the touch, if any (missing in TOUCH_UP)
     */
    private void evaluateMain(int bowAction, StrokePoint strokePoint)
        {
        Scribe.locus( Debug.TOUCH_VERBOSE );

        // At TOUCH_DOWN strokePoint storage is always needed (even if it will be a META stroke),
        // otherwise ACTION_MOVE cannot differentiate between HOLD and MOVE
        // ((or it should imply one more 'if'))

        // Store the stroke if:
        // - stroke is not finished (strokePoint is not null)
        // - stroke is not hold
        if (bowAction != TOUCH_UP) // == (strokePoint != null)
            {
            // if (bowAction != TOUCH_HOLD) - but HOLD never calls evaluate main
            strokePoints.add(strokePoint);

            if ( layout.softBoardData.displayPaths )
                {
                if ( bowAction == TOUCH_DOWN )
                    {
                    lastStrokePath = new Path();
                    strokePaths.add( lastStrokePath );

                    lastStrokePath.moveTo( strokePoint.canvasX, strokePoint.canvasY );
                    // Scribe.debug(Debug.VIEW, "Touch DOWN. New path is moving to " +
                    //         strokePoint.canvasX + ", " + strokePoint.canvasY);
                    }
                else if ( lastStrokePath != null )// TOUCH_MOVE TOUCH_UP
                    {
                    lastStrokePath.lineTo( strokePoint.canvasX, strokePoint.canvasY );
                    // Scribe.debug(Debug.VIEW, "Touch MOVE. Path is lining to " +
                    //         strokePoint.canvasX + ", " + strokePoint.canvasY);
                    }
                this.invalidate();
                }

            // !! Shorter stroke can be drawn !!
            // while ( strokePoints.size() > 150 )
            //     strokePoints.remove( 0 );

            if (layout.softBoardData.displayStroke) this.invalidate();
            }
        // if bowAction == TOUCH_UP - strokePoints will be cleared later

        // FIRST: find out the touchCode for this new touch
        int newBowTouchCode = mainTouchBow.touchCode;


        if (bowAction == TOUCH_DOWN)
            {
            newBowTouchCode = Layout.touchCodeFromColor(layout.colorFromMap(strokePoint.canvasX, strokePoint.canvasY));
            // Scribe.debug( Debug.TOUCH, "MAIN pointer DOWN.");
            }
        else if (bowAction == TOUCH_MOVE)
            {
            // touchCode changes only if move arrives inside the rim
            int color = layout.colorFromMap(strokePoint.canvasX, strokePoint.canvasY);
            newBowTouchCode = Layout.touchCodeFromColor(color);

            // touch is on the same button - independently from center/outer rim
            if ( newBowTouchCode == mainTouchBow.touchCode )
                {
                mainTouchBow.increaseMoveCounter();
                }

            // invalidate touch code if touch is not arrived in the center of an other button
            else if ( Layout.outerRimFromColor(color) )
                {
                newBowTouchCode = mainTouchBow.touchCode;
                }
            }
        else if (bowAction == TOUCH_UP)
            {
            // UP is the same as MOVE to an empty button
            newBowTouchCode = Layout.EMPTY_TOUCH_CODE;
            // Scribe.debug( Debug.TOUCH, "MAIN pointer UP.");

            // Main stroke was freed because of TOUCH_UP action
            strokePoints.clear(); // if we want the stroke to disappear
            // Because this.invalidate will be called in the next section, this is not needed:
            // if (layout.softBoardData.displayStroke) this.invalidate();
            }
        // else TOUCH_HOLD - but HOLD never calls evaluateMain - will not change the touchCode

        if (mainTouchBow.touchCode != newBowTouchCode)
            {
            // THE TOUCHED BUTTON IS CHANGED!
            debug( Debug.TOUCH, "MAIN pointer arrived to a new touchCode: " + mainTouchBow.touchCode + " -> " + newBowTouchCode);

            // view should be invalidated
            // not only because the touch (controlled by displayTouch),
            // but meta-state could also change!
            this.invalidate();

            //  check bow's finish - finish previous button
            if (mainTouchBow.buttonMainTouch != null)
                {
                debug( Debug.TOUCH, "Previous button is released: " + mainTouchBow.buttonMainTouch.getFirstString() );

                // meta check could be here, after finishing the next main-stream button
                // but in this case we should finish here

                // Clears meta ON states
                typeMetaState();

                // PacketTest sets autoCaps state
                if (bowAction == TOUCH_UP)
                    mainTouchBow.buttonMainTouch.mainTouchEnd(true);
                else
                    mainTouchBow.buttonMainTouch.mainTouchEnd(false);

                // Clears link ON states - this could clear mainTouchBow, too
                typeLinkState();

                // if layout was changed during type, no further buttons could be evaluated!
                if ( pointerChangeFlag == BOARD_CHANGE )
                	{
                    return;
                    }

                }

            if (newBowTouchCode != Layout.EMPTY_TOUCH_CODE)
                {
                // touch is on a new and valid button
                Button newBowButton = layout.buttons[newBowTouchCode];

                // Button is on MAIN TOUCH
                if (newBowButton instanceof ButtonMainTouch)
                    {
                    debug( Debug.TOUCH, "A new MAIN bow is started, MAIN button touched: " + newBowButton.getFirstString() );
                    // start a new MAIN bow
                    mainTouchBow = new MainTouchBow( newBowTouchCode, (ButtonMainTouch)newBowButton );

                    if (bowAction == TOUCH_DOWN)
                        mainTouchBow.buttonMainTouch.mainTouchStart(true);
                    else
                        mainTouchBow.buttonMainTouch.mainTouchStart(false);

                    // meta check could be here, just after the first event

                    // new MAIN bow created, evaluation is finished

                    // TIMING EVENT; could be in mainTouchStart
                    layout.softBoardData.buttonCounter.measure(1);
                    layout.softBoardData.showTiming();

                    return;
                    }

                else if (newBowButton instanceof ButtonMultiTouch)
                    {
                    // if MULTI -> put in MULTI
                    // MULTI TOUCH can start here only!!
                    debug( Debug.TOUCH, "MAIN pointer changed to MULTI. MULTI button touched: " + newBowButton.getFirstString() );

                    multiTouchPointers.put(strokePointerId, new MultiTouchBow(newBowTouchCode, (ButtonMultiTouch) newBowButton));

                    pointerChangeFlag = MAIN_TO_META_CHANGE;
                    // ButtonUse should also call boardChange()!!

                    ((ButtonMultiTouch) newBowButton).multiTouchEvent(ButtonMultiTouch.META_TOUCH);
                    // MULTI (indicator) keys could change without the change of the MAIN
                    // but this.invalidate(); was called previously

                    // Similar as _touchUp(), but previous button will be not finished
                    strokePointerId = -1;
                    newBowTouchCode = Layout.EMPTY_TOUCH_CODE;
                    }

                // ButtonMainTouch finished and returned with a real bow previously
                // ButtonMultiTouch ends here
                // all other Button sub-classes and non-defined buttons end here
                }

            // "outside" areas ends here
            debug( Debug.TOUCH, "MAIN pointer has no attached button.");
            mainTouchBow = new MainTouchBow(newBowTouchCode, null);
            }
        else // same bow
            {
            // check bow's long
            if ( mainTouchBow.isLong() && mainTouchBow.buttonMainTouch != null)
                {
                debug(Debug.TOUCH, "LONG touch is detected.");
                mainTouchBow.buttonMainTouch.mainTouchSecondary(ButtonMainTouch.ON_CIRCLE);
                mainTouchBow.resetMoveAndPressureCounter();

                // TIMING EVENT - could be in mainTouchSecondary
                layout.softBoardData.buttonCounter.measure(1);
                layout.softBoardData.showTiming();
                }

            // check bow's press
            if ( mainTouchBow.isPressed() && mainTouchBow.buttonMainTouch != null)
                {
                debug( Debug.TOUCH, "PRESS touch is detected." );
                mainTouchBow.buttonMainTouch.mainTouchSecondary(ButtonMainTouch.ON_HARD_PRESS);
                mainTouchBow.resetMoveAndPressureCounter();

                // TIMING EVENT - could be in mainTouchSecondary
                layout.softBoardData.buttonCounter.measure(1);
                layout.softBoardData.showTiming();
                }

            // bow's repeat - is checked by the repeat-handler
            // no timing for repeated buttons
            }
        }


    /**
     ** DRAWING PHASE
     **/

    @Override
    protected void onDraw(Canvas canvas)
        {
        Scribe.locus( Debug.DRAW_VERBOSE );

        // Navigation Bar seriously fails
        // This cannot be previously, it is not working even in onSizeChanged.
        if (fakeView)
            {
            debug( Debug.VIEW, "onDraw is requesting new Layout because of fake view." );

            fakeView = false;
            requestLayout();
            return;
            }

        /*Scribe.debug( Debug.LAYOUT, "Layout HEIGHT at draw: " + getHeight() +
                ", measured height: " + getMeasuredHeight() +
                " , top: " + getTop() +
                " , padding: " + getPaddingTop() + ", " + getPaddingBottom() +
                ", offset: " + layout.layoutYOffset);*/

        //int loc[] = new int[2];
        //getLocationOnScreen( loc );
        //debug( Debug.LAYOUT, "Layout SCREEN LOCATION at draw: " +
        //        loc[0] + ", " + loc[1] );

        // layout.drawLayoutMap(canvas);

        // !! This cuold be part of draw… methods
        Button.setLineFromLayout(layout);
        
        layout.drawLayoutPicture(canvas);

        // ChangedButtons - draw over the bitmap, too
        // !! ha nem touched ??
        layout.drawChangedButtons(canvas);

        // TouchedButton - draw over the bitmap!
        if (mainTouchBow.buttonMainTouch != null && layout.softBoardData.displayTouch)
            mainTouchBow.buttonMainTouch.drawButtonTouched(canvas);

        // TouchedPoints - if needed
        if (layout.softBoardData.displayPaths)
            {
            for ( Path path : strokePaths )
                {
                canvas.drawPath(path, pathPaint);
                }
            }

        if (layout.softBoardData.displayStroke)
            {
            for (StrokePoint touchedPoint : strokePoints)
                {
                canvas.drawCircle(touchedPoint.canvasX, touchedPoint.canvasY, 3f, strokePaint);
                }
            }

        // Monitor row to write out speed data
        layout.drawMonitorRow( canvas );
        }

    }
