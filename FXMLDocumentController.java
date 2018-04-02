package clock;

import static clock.ClickDragState.*;
import com.fazecast.jSerialComm.*;
import java.net.URL;
import java.util.ArrayList;
import java.util.ResourceBundle;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Circle;
import javafx.util.Duration;
import static serialcomms.SerialOutput.*;
import java.awt.*;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import java.util.logging.Level;
import java.util.logging.Logger;

public class FXMLDocumentController implements Initializable {

  //GUI elements
  @FXML
  private Button updateTimeButton;
  @FXML
  private Button updateAlarmButton;
  @FXML
  private Button updateWeatherForecast;
  @FXML
  private Button updateRemoteClock;
  @FXML
  private Label text;
  @FXML
  private Circle ledPositionOne;
  @FXML
  private Circle ledPositionTwo;
  @FXML
  private Circle ledPositionThree;
  @FXML
  private Circle ledPositionFour;
  @FXML
  private Circle ledPositionFive;
  @FXML
  private Circle ledPositionSix;
  @FXML
  private Circle ledPositionSeven;
  @FXML
  private Circle ledPositionEight;
  @FXML
  private Circle ledPositionNine;
  @FXML
  private Circle ledPositionTen;
  @FXML
  private Circle ledPositionEleven;
  @FXML
  private Circle ledPositionTwelve;
  @FXML
  private Circle oneMinuteRemainderLed;
  @FXML
  private Circle twoMinuteRemainderLed;
  @FXML
  private Circle threeMinuteRemainderLed;
  @FXML
  private Circle fourMinuteRemainderLed;
  @FXML
  private Slider slider;
  @FXML
  private Circle led5;
  @FXML
  private Circle led6;
  @FXML
  private Circle led9;
  @FXML
  private Circle led10;
  @FXML
  private Circle led11;
  @FXML
  private Circle led12;
  @FXML
  private Circle led13;
  @FXML
  private Circle led14;
  @FXML
  private Circle led15;
  @FXML
  private Circle led18;
  @FXML
  private Circle led19;
  @FXML
  private Circle led20;
  @FXML
  private Circle led21;
  @FXML
  private TextField hourTextField;
  @FXML
  private TextField minute;
  @FXML
  private Button selectMinute;
  @FXML
  private Button selectHour;

  private static final Logger LOGGER = Logger.getLogger(FXMLDocumentController.class.getName());

  static final Paint HOUR_COLOR_PM = Color.BLUE;
  static final Paint HOUR_COLOR_AM = Color.ORANGE;
  static final Paint MINUTE_COLOR_PM = Color.PURPLE;
  static final Paint MINUTE_COLOR_AM = Color.YELLOW;
  static final Paint LED_OFF = Color.gray(.3, 1);

  public ClockModel clockModel;
  static final int HOUR = 0;
  static final int MIN = 1;
  static final int SEC = 2;
  static final int MIDDAY = 3;
  static final int AM = 0;
  static final int PM = 1;

  static final int ALARM_END_TIME = 10;
  private int lastAlarmStart = -10;

  static final Boolean DISABLE_REFRESH = false; //VALUE should be false
  static final double ANIMATION_LENGTH = 3;   //VALUE should be 3
  static final float ANIMATION_START_TIME = 30; //VALUE should be 30
  static final Boolean ENABLE_ANIMATION = true; //VALUE should be true
  static final Boolean DEBUG_MODE = false; //VALUE should be false
  static final double REFRESH_RATE = 0.1; // refresh rate in seconds
  static Boolean sendSplashMessage = true;
  SerialPort port = null;

  ClickDragState clickDragState = DISABLED;
  private int selectedOuterLed = 12;
  private int selectedInnerLed = 0;

  private String transferProgress = "Transfer Progress: - ";

  public FXMLDocumentController() {
    this.clockModel = new ClockModel();
  }

  /**
   * Start the main loop which refreshes the display
   */
  @Override
  public void initialize(URL url, ResourceBundle rb) {
    Timeline timeline = new Timeline(
        new KeyFrame(Duration.seconds(REFRESH_RATE),
            new EventHandler<ActionEvent>() {
          @Override
          public void handle(ActionEvent actionEvent) {
            updateDisplay();
          }
        }));
    timeline.setCycleCount(Animation.INDEFINITE);
    timeline.play();
  }

  /**
   * The main method for controlling the display, run continually
   */
  private void updateDisplay() {
    lightAllLeds(LED_OFF);
    if (DISABLE_REFRESH) {
      return;
    }
    showInformation();

    int seconds = clockModel.getCurrent24HourTime()[SEC];
    float preciseSeconds = seconds + clockModel.getTenthOfSecond();

    //CLICK & DRAG DISPLAY            
    if (clickDragState == SET_MINUTE) {
      displayMinutesDragged();
    } else if (clickDragState == SET_HOUR) {
      displayHourDragged();
      //ALARM
    } else if (seconds < ALARM_END_TIME && clockModel.alarmActive()) {
      displayAlarmAnimation(preciseSeconds);
      if (java.lang.Math.abs(seconds - lastAlarmStart) >= 2) {
        Toolkit.getDefaultToolkit().beep();
        lastAlarmStart = seconds;
      }
      //WEATHER ANIMATION
    } else if (preciseSeconds > ANIMATION_START_TIME && ENABLE_ANIMATION
        && preciseSeconds < (ANIMATION_START_TIME + ANIMATION_LENGTH)) {
      displayForecast(preciseSeconds);
    } else {
      //DISPLAY TIME
      displayModelTime(seconds);
    }
  }

  /**
   * Displays text messages on the GUI
   */
  private void showInformation() {
    String textToDisplay = transferProgress;
    String debugText = "\n24 hr: " + clockModel.getText24HourTime()
        + "12 hr: " + clockModel.getText12HourTime()
        + "Weather: " + clockModel.getWeatherForecast()[0] + ", "
        + clockModel.getWeatherForecast()[1]
        + "\t\t24hr Alarm: " + clockModel.alarmTwentyFourHourTime[HOUR] + ":"
        + clockModel.alarmTwentyFourHourTime[MIN];
    if (DEBUG_MODE) {
      textToDisplay += debugText;
    }
    text.setText(textToDisplay);
  }

  /**
   * Sends the clock data to the remote clock
   */
  @FXML
  private void updateRemoteClock() {
    exitDragTimeMode();

    int seconds = clockModel.getCurrent24HourTime()[SEC];
    int hours = clockModel.getCurrent24HourTime()[HOUR];
    int minutes = clockModel.getCurrent24HourTime()[MIN];
    boolean alarm = clockModel.validAlarm();
    int alarmHours = clockModel.alarmTwentyFourHourTime[HOUR];
    int alarmMinutes = clockModel.alarmTwentyFourHourTime[MIN];
    char firstWeather = clockModel.getWeatherStateChar(0);
    char secondWeather = clockModel.getWeatherStateChar(1);
    String dataToSend = String.format("Sending remote clock Time: %02d:%02d:%02d, "
        + "Alarm %b %02d:%02d, Weather [%c,%c]",
        hours, minutes, seconds, alarm, alarmHours, alarmMinutes, firstWeather, secondWeather);
    LOGGER.log(Level.INFO, dataToSend);

    //Get port if haven't already
    if (port == null) {
      port = openFtdiPort();
      if (port == null) {
        showError("Please reconnect the IR dongle.");
        transferProgress = "Transfer Progress: Failed";
        sendSplashMessage = true;
      } else {
        LOGGER.log(Level.INFO, "Saving port");
      }
    }
    //Send data
    if (port != null) {
      LOGGER.log(Level.INFO, "Sending...0%");
      transferProgress = "Transfer Progress: Sending...0%";
      Boolean messageSent = false;
      try {
        messageSent = sendMessage(port, true, hours, minutes, seconds, alarm, alarmHours,
            alarmMinutes, true, firstWeather, secondWeather, sendSplashMessage);
      } catch (Exception e) {
        port = null; //if sending data fails here dongle probably has been removed
        transferProgress = "Transfer Progress: Failed";
        showError("Please try again");
        sendSplashMessage = true;
      }

      if (!messageSent) {
        port = null; //if message returned false dongle probably has been removed 
        LOGGER.log(Level.INFO, "Sending...Failed");
        transferProgress = "Transfer Progress: Failed";
        showError("Please try again");
        sendSplashMessage = true;
      } else {
        LOGGER.log(Level.INFO, "Sending...100%");
        transferProgress = "Transfer Progress: Transfer Complete";
        sendSplashMessage = false;
      }
    } else {
      sendSplashMessage = true;
    }
  }

  // ALARM methods
  /**
   * The animation for the alarm
   *
   * @param animationTime the time over which the animation is to be displayed
   */
  private void displayAlarmAnimation(float animationTime) {
    int[] frameOne = {2, 5, 6, 9, 10, 11, 12, 13, 14, 15, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 30};
    int[] frameTwo = {2, 5, 6, 9, 10, 11, 12, 13, 14, 15, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 29};
    int[] frameThree = {2, 5, 6, 9, 10, 11, 12, 13, 14, 15, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 30};
    int[] frameFour = {2, 5, 6, 9, 10, 11, 12, 13, 14, 15, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28};

    ArrayList<int[]> filmReel = new ArrayList<int[]>();
    for (int i = 0; i < 10; i++) {
      filmReel.add(frameOne);
      filmReel.add(frameTwo);
      filmReel.add(frameThree);
      filmReel.add(frameFour);
    }

    Color color;
    if ((int) animationTime % 2 == 0) {
      color = Color.YELLOW;
    } else {
      color = Color.RED;
    }

    lightLeds(selectFrame(animationTime, filmReel, ALARM_END_TIME), color);
  }

  /**
   * Checks 'hour' is between 1 and 12 inclusive
   *
   * @param hour The hour to check
   * @return Returns true if the hour is between 1 and 12 (inclusive), else false
   */
  private Boolean validHour(int hour) {
    if (hour < 1 || hour > 12) {
      LOGGER.log(Level.WARNING, "Hour is not 1 to 12");
      showError("Hour must be a valid time");
      return false;
    }
    return true;
  }

  /**
   * Checks 'minute' is between 0 and 59 inclusive
   *
   * @param min The minute to check
   * @return Returns true if the minute is between 0 and 59 (inclusive), else false
   */
  private Boolean validMinute(int min) {
    if (min < 0 || min > 59) {
      LOGGER.log(Level.WARNING, "Minute is not 0 to 59");
      showError("Minute must be a valid time");
      return false;
    }
    return true;
  }

  /**
   * Updates the model's stored alarm time
   *
   * @param event The event which calls this method
   */
  @FXML
  private void updateAlarmTime(ActionEvent event) {
    exitDragTimeMode();
    int[] twelveHourTime = {0, 0, 0, 0};
    twelveHourTime[MIDDAY] = (int) slider.getValue();
    try {
      twelveHourTime[HOUR] = Integer.parseInt(hourTextField.getText());
      if (!validHour(twelveHourTime[HOUR])) {
        return;
      } else if (twelveHourTime[HOUR] == 0) {
        twelveHourTime[HOUR] = 12;
      }
    } catch (Exception e) {
      LOGGER.log(Level.WARNING, "Invalid hour input");
      showError("Hour must be a valid number");
      return;
    }
    try {
      twelveHourTime[MIN] = Integer.parseInt(minute.getText());
      if (!validMinute(twelveHourTime[MIN])) {
        return;
      }
    } catch (Exception e) {
      LOGGER.log(Level.WARNING, "Invalid minute input");
      showError("Minute must be a valid number");
      return;
    }

    int[] twentyFourHourTime = clockModel.convert12HourTimeTo24HourTime(twelveHourTime);
    clockModel.setAlarm(twentyFourHourTime);
  }

  // WEATHER methods
  /**
   * Downloads updates to the weather forecast in the model
   *
   * @param event The event which calls this method
   */
  @FXML
  private void updateWeatherForecast(ActionEvent event) {
    exitDragTimeMode();
    clockModel.updateForecast();
  }

  /**
   * Chooses the appropriate stored forecast to display
   *
   * @param tinySeconds The current seconds value of the time
   */
  private void displayForecast(float tinySeconds) {
    if (tinySeconds < (ANIMATION_START_TIME + ANIMATION_LENGTH / 2)) {
      //show first forecast
      displayAnimation(clockModel.getWeatherForecast()[0],
          tinySeconds - ANIMATION_START_TIME);
    } else {
      //show second forecast
      displayAnimation(clockModel.getWeatherForecast()[1],
          tinySeconds - ANIMATION_START_TIME - (float) (ANIMATION_LENGTH / 2));
    }
  }

  /**
   * Displays the specified forecast at the specified time in the animation
   *
   * @param forecast The forecast to display
   * @param animationPosition The time to animate from, relative to the animation start
   */
  private void displayAnimation(WeatherState forecast, float animationPosition) {
    switch (forecast) {
      case SUNNY:
        displaySunAnimation(animationPosition);
        break;
      case CLOUDY:
        displayCloudyAnimation(animationPosition);
        break;
      case RAIN:
        displayRainAnimation(animationPosition);
        break;
      case WINDY:
        displayWindyAnimation(animationPosition);
        break;
      case STORM:
        displayStormAnimation(animationPosition);
        break;
      default:
        LOGGER.log(Level.SEVERE, "The forecast to be displayed could not be recognised");
    }
  }

  /**
   * Selects the animation frame to show
   *
   * @param animationPosition The time in the animation for which the frame is required
   * @param frames The frames of the animation
   * @param animationLength The desired animation total runtime
   * @return The animation frame to be displayed at the specified time
   */
  public int[] selectFrame(float animationPosition, ArrayList<int[]> frames, double animationLength) {
    int index = (int) (animationPosition * frames.size() / animationLength);
    if (index > frames.size() - 1) {
      index = frames.size() - 1;
    }
    return frames.get(index);
  }

  /**
   * Displays the sun animation
   *
   * @param animationTime The time passed since the start of the animation
   */
  private void displaySunAnimation(float animationTime) {
    int[] frameOne = {13, 14, 19, 20};
    int[] frameTwo = {13, 14, 19, 20, 9, 11, 23, 26};
    int[] frameThree = {13, 14, 19, 20, 9, 11, 23, 26, 3, 4, 28, 29, 16, 17};

    ArrayList<int[]> filmReel = new ArrayList<int[]>();
    filmReel.add(frameOne);
    filmReel.add(frameTwo);
    filmReel.add(frameThree);

    lightLeds(selectFrame(animationTime, filmReel, ANIMATION_LENGTH / 2), Color.YELLOW);
  }

  /**
   * Displays the storm animation
   *
   * @param animationTime The time passed since the start of the animation
   */
  private void displayStormAnimation(float animationTime) {
    int[] staticCloud = {2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15};
    lightLeds(staticCloud, Color.WHITE);

    int[] frameOne = {19, 23};
    int[] frameTwo = {19, 23, 24, 25};
    int[] frameThree = {19, 23, 24, 25, 30};
    int[] frameFour = {25, 30};
    int[] frameFive = {};

    ArrayList<int[]> filmReel = new ArrayList<int[]>();
    filmReel.add(frameOne);
    filmReel.add(frameTwo);
    filmReel.add(frameThree);
    filmReel.add(frameFour);
    filmReel.add(frameFive);

    lightLeds(selectFrame(animationTime, filmReel, ANIMATION_LENGTH / 2), Color.YELLOW);
  }

  /**
   * Displays the rain animation
   *
   * @param animationTime The time passed since the start of the animation
   */
  private void displayRainAnimation(float animationTime) {
    int[] frameOne = {2, 5, 6};
    int[] frameTwo = {2, 5, 6, 9, 10, 11, 12, 13, 14, 15};
    int[] frameThree = {2, 5, 6, 9, 10, 11, 12, 13, 14, 15, 18, 19, 20, 21, 23, 24, 25, 26};
    int[] frameFour = {22, 28, 30, 29, 27};

    ArrayList<int[]> filmReel = new ArrayList<int[]>();
    filmReel.add(frameOne);
    filmReel.add(frameTwo);
    filmReel.add(frameThree);
    filmReel.add(frameFour);

    lightLeds(selectFrame(animationTime, filmReel, ANIMATION_LENGTH / 2), Color.AQUA);
  }

  /**
   * Displays the cloudy animation
   *
   * @param animationTime The time passed since the start of the animation
   */
  private void displayCloudyAnimation(float animationTime) {
    int[] frameOne = {3, 7, 5, 9, 10, 12, 13};
    int[] frameTwo = {3, 7, 5, 9, 10, 12, 13, 2, 6, 11, 14};
    int[] frameThree = {3, 7, 5, 9, 10, 12, 13, 2, 6, 11, 14, 4, 8, 15};
    int[] frameFour = {5, 9, 10, 13, 2, 6, 11, 14, 4, 8, 15};
    int[] frameFive = {10, 6, 11, 14, 4, 8, 15};

    ArrayList<int[]> filmReel = new ArrayList<int[]>();
    filmReel.add(frameOne);
    filmReel.add(frameTwo);
    filmReel.add(frameThree);
    filmReel.add(frameFour);
    filmReel.add(frameFive);

    lightLeds(selectFrame(animationTime, filmReel, ANIMATION_LENGTH / 2), Color.WHITE);
  }

  /**
   * Displays the windy animation
   *
   * @param animationTime The time passed since the start of the animation
   */
  private void displayWindyAnimation(float animationTime) {
    ArrayList<int[]> filmReel = new ArrayList<int[]>();
    int[] frameOne = {16, 12, 18};
    int[] frameTwo = {12, 18, 13, 19};
    int[] frameThree = {13, 14, 19, 20};
    int[] frameFour = {14, 15, 20, 21};
    int[] frameFive = {15, 21, 17, 27, 8};

    filmReel.add(frameOne);
    filmReel.add(frameTwo);
    filmReel.add(frameThree);
    filmReel.add(frameFour);
    filmReel.add(frameFive);

    lightLeds(selectFrame(animationTime, filmReel, ANIMATION_LENGTH / 2), Color.LIGHTBLUE);
  }

  // SET & DISPLAY TIME methods
  /**
   * Updates the stored time
   *
   * @param event The event which calls this method
   */
  @FXML
  private void updateTime(ActionEvent event) {
    exitDragTimeMode();
    int[] twelveHourTime = {0, 0, 0, 0};
    twelveHourTime[MIDDAY] = (int) slider.getValue();
    try {
      twelveHourTime[HOUR] = Integer.parseInt(hourTextField.getText());
      if (!validHour(twelveHourTime[HOUR])) {
        return;
      } else if (twelveHourTime[HOUR] == 0) {
        twelveHourTime[HOUR] = 12; // prevents the case when 12 o'clock => 0 
      }
    } catch (Exception e) {
      LOGGER.log(Level.WARNING, "Invalid hour input");
      showError("Hour must be a valid number");
      return;
    }
    try {
      twelveHourTime[MIN] = Integer.parseInt(minute.getText());
      if (!validMinute(twelveHourTime[MIN])) {
        return;
      }
    } catch (Exception e) {
      LOGGER.log(Level.WARNING, "Invalid minute input");
      showError("Minute must be a valid number");
      return;
    }

    int[] twentyFourHourTime = clockModel.convert12HourTimeTo24HourTime(twelveHourTime);
    clockModel.setTime(twentyFourHourTime);
  }

  /**
   * Displays the time stored in the model
   *
   * @param seconds The current value of the time's seconds
   */
  private void displayModelTime(int seconds) {
    Paint minuteColor = MINUTE_COLOR_AM;
    Paint hourColour = HOUR_COLOR_AM;
    if (clockModel.getCurrent12HourTime()[MIDDAY] == PM) {
      minuteColor = MINUTE_COLOR_PM;
      hourColour = HOUR_COLOR_PM;
    }

    lightHourLed(clockModel.getCurrent12HourTime()[HOUR], hourColour);
    lightRemainderLed(clockModel.getCurrent12HourTime()[MIN] % 5, minuteColor);
    //blink the following
    if (seconds % 2 == 0) {
      lightHourLed(clockModel.getCurrent12HourTime()[MIN] / 5, minuteColor); //minute hand
    }
  }

  /**
   * Lights the specified hour LED the colour specified
   *
   * @param hour The hour of the LED to colour
   * @param color The colour to light the LED
   */
  public void lightHourLed(int hour, Paint color) {
    switch (hour % 12) {
      case 0:
        ledPositionTwelve.setFill(color);
        break;
      case 1:
        ledPositionOne.setFill(color);
        break;
      case 2:
        ledPositionTwo.setFill(color);
        break;
      case 3:
        ledPositionThree.setFill(color);
        break;
      case 4:
        ledPositionFour.setFill(color);
        break;
      case 5:
        ledPositionFive.setFill(color);
        break;
      case 6:
        ledPositionSix.setFill(color);
        break;
      case 7:
        ledPositionSeven.setFill(color);
        break;
      case 8:
        ledPositionEight.setFill(color);
        break;
      case 9:
        ledPositionNine.setFill(color);
        break;
      case 10:
        ledPositionTen.setFill(color);
        break;
      case 11:
        ledPositionEleven.setFill(color);
        break;
      default:
        LOGGER.log(Level.SEVERE, "Attempted to light hour LED outside of 1 to 12");
        break;
    }
  }

  /**
   * Lights the minute remainder LEDS
   *
   * @param remainder The value of the minute remainder
   * @param color The colour to use
   */
  public void lightRemainderLed(int remainder, Paint color) {
    switch (remainder) {
      case 4:
        fourMinuteRemainderLed.setFill(color);
      case 3:
        threeMinuteRemainderLed.setFill(color);
      case 2:
        twoMinuteRemainderLed.setFill(color);
      case 1:
        oneMinuteRemainderLed.setFill(color);
      // fall through
      default:
        break;
    }
  }

  /**
   * Light the LED specified
   *
   * @param led The LED to light
   * @param color The colour to use
   */
  public void lightLed(int led, Paint color) {
    switch (led) {
      case 2:
        ledPositionTwelve.setFill(color);
        break;
      case 3:
        ledPositionEleven.setFill(color);
        break;
      case 4:
        ledPositionOne.setFill(color);
        break;
      case 5:
        led5.setFill(color);
        break;
      case 6:
        led6.setFill(color);
        break;
      case 7:
        ledPositionTen.setFill(color);
        break;
      case 8:
        ledPositionTwo.setFill(color);
        break;
      case 9:
        led9.setFill(color);
        break;
      case 10:
        led10.setFill(color);
        break;
      case 11:
        led11.setFill(color);
        break;
      case 12:
        led12.setFill(color);
        break;
      case 13:
        led13.setFill(color);
        break;
      case 14:
        led14.setFill(color);
        break;
      case 15:
        led15.setFill(color);
        break;
      case 16:
        ledPositionNine.setFill(color);
        break;
      case 17:
        ledPositionThree.setFill(color);
        break;
      case 18:
        led18.setFill(color);
        break;
      case 19:
        led19.setFill(color);
        break;
      case 20:
        led20.setFill(color);
        break;
      case 21:
        led21.setFill(color);
        break;
      case 22:
        ledPositionEight.setFill(color);
        break;
      case 23:
        oneMinuteRemainderLed.setFill(color);
        break;
      case 24:
        twoMinuteRemainderLed.setFill(color);
        break;
      case 25:
        threeMinuteRemainderLed.setFill(color);
        break;
      case 26:
        fourMinuteRemainderLed.setFill(color);
        break;
      case 27:
        ledPositionFour.setFill(color);
        break;
      case 28:
        ledPositionSeven.setFill(color);
        break;
      case 29:
        ledPositionFive.setFill(color);
        break;
      case 30:
        ledPositionSix.setFill(color);
        break;
      default:
        break;
    }
  }

  /**
   * Light all LEDS the colour specified
   *
   * @param leds The LEDS to light
   * @param color The colour to use
   */
  public void lightLeds(int[] leds, Paint color) {
    for (int x : leds) {
      lightLed(x, color);
    }
  }

  /**
   * Colours all LEDS to the color specified
   *
   * @param color The color to use
   */
  public void lightAllLeds(Paint color) {
    final int NUMBER_OF_LEDS = 31;
    for (int x = 1; x < NUMBER_OF_LEDS; x++) {
      lightLed(x, color);
    }
  }

  //CLICK & DRAG methods
  /**
   * Display the minutes selected via click and drag
   */
  public void displayMinutesDragged() {
    Paint minuteColor = MINUTE_COLOR_AM;
    if ((int) slider.getValue() == PM) {
      minuteColor = MINUTE_COLOR_PM;
    }
    lightRemainderLed(selectedInnerLed, minuteColor);
    lightHourLed(selectedOuterLed, minuteColor);
    int minutes = (selectedOuterLed * 5 + selectedInnerLed) % 60;
    minute.setText("" + minutes);
  }

  /**
   * Display the hour selected via click and drag
   */
  public void displayHourDragged() {
    Paint hourColour = HOUR_COLOR_AM;
    if ((int) slider.getValue() == PM) {
      hourColour = HOUR_COLOR_PM;
    }
    lightHourLed(selectedOuterLed, hourColour);
    hourTextField.setText("" + selectedOuterLed);
  }

  /**
   * Initiates the Click & Drag for selecting minutes
   *
   * @param e The event which calls this method
   */
  @FXML
  private void selectMinute(ActionEvent e) {
    if (clickDragState == SET_MINUTE) {
      exitDragTimeMode();
    } else {
      clickDragState = SET_MINUTE;
      selectedOuterLed = 12;  //default outer led to 12
      selectedInnerLed = 0;   //default inner led to 0
      selectHour.setText("Choose Hour via Click & Drag");
      selectMinute.setText("Exit Click & Drag");
    }
  }

  /**
   * Initiates the Click & Drag for selecting an hour
   *
   * @param e The event which calls this method
   */
  @FXML
  private void selectHour(ActionEvent e) {
    if (clickDragState == SET_HOUR) {
      exitDragTimeMode();
    } else {
      clickDragState = SET_HOUR;
      selectedOuterLed = 12;
      selectMinute.setText("Choose Minute via Click & Drag");
      selectHour.setText("Exit Click & Drag");
    }
  }

  /**
   * Exits from the Click & Drag mode
   */
  private void exitDragTimeMode() {
    clickDragState = DISABLED;
    selectMinute.setText("Choose Minute via Click & Drag");
    selectHour.setText("Choose Hour via Click & Drag");
  }

  /**
   * Initiates a Click & Drag sequence, saving the LED
   *
   * @param e The drag event on the outer LED circle which initiates the sequence
   */
  @FXML
  private void startOuterDragSequence(MouseEvent e) {
    if (clickDragState != DISABLED && e.getSource() instanceof Circle) {
      ((Circle) e.getSource()).startFullDrag();
      selectedOuterLed = selectedLedToNumber((Circle) e.getSource());
    }
  }

  /**
   * Continues a Click & Drag sequence, saving the LED
   *
   * @param e The enter event on the outer LED circle which continues the drag sequence
   */
  @FXML
  private void continueOuterDragSequence(MouseEvent e) {
    if (clickDragState != DISABLED && e.getSource() instanceof Circle) {
      selectedOuterLed = selectedLedToNumber((Circle) e.getSource());
    }
  }

  /**
   * Saves the selected minute remainder LED when clicked
   *
   * @param e The click event on the minute remainder LED
   */
  @FXML
  private void mouseClickInner(MouseEvent e) {
    if (clickDragState == SET_MINUTE && e.getSource() instanceof Circle) {
      int potentialSelectedInnerLed = selectedLedToNumber((Circle) e.getSource());
      if (selectedInnerLed == potentialSelectedInnerLed) {
        selectedInnerLed = 0;
      } else {
        selectedInnerLed = potentialSelectedInnerLed;
      }
    }
  }

  /**
   * Saves the selected minute remainder LED when entered
   *
   * @param e The enter event on the remainder LED that continues a drag
   */
  @FXML
  private void continueInnerDragSequence(MouseEvent e) {
    if (clickDragState == SET_MINUTE && e.getSource() instanceof Circle) {
      selectedInnerLed = selectedLedToNumber((Circle) e.getSource());
    }
  }

  /**
   * Starts a drag sequence when a drag is initiated on the background
   *
   * @param e The drag event on the background which initiates a drag sequence
   */
  @FXML
  private void startAnchorPaneDragSequence(MouseEvent e) {
    if (clickDragState != DISABLED && e.getSource() instanceof Node) {
      ((Node) e.getSource()).startFullDrag();
    }
  }

  /**
   * Saves the selected minute remainder LED when starting a drag
   *
   * @param e The drag event on the minute remainder LEDs that starts a drag
   */
  @FXML
  private void startInnerDragSequence(MouseEvent e) {
    if (clickDragState == SET_MINUTE && e.getSource() instanceof Circle) {
      ((Circle) e.getSource()).startFullDrag();
      selectedInnerLed = selectedLedToNumber((Circle) e.getSource());
    }
  }

  /**
   * Provides the number associated with the specified LED (circle) object
   *
   * @param led The led to identify the number of
   * @return Returns the number of the LED
   */
  int selectedLedToNumber(Circle led) {
    if (led == ledPositionOne) {
      return 1;
    } else if (led == ledPositionTwo) {
      return 2;
    } else if (led == ledPositionThree) {
      return 3;
    } else if (led == ledPositionFour) {
      return 4;
    } else if (led == ledPositionFive) {
      return 5;
    } else if (led == ledPositionSix) {
      return 6;
    } else if (led == ledPositionSeven) {
      return 7;
    } else if (led == ledPositionEight) {
      return 8;
    } else if (led == ledPositionNine) {
      return 9;
    } else if (led == ledPositionTen) {
      return 10;
    } else if (led == ledPositionEleven) {
      return 11;
    } else if (led == oneMinuteRemainderLed) {
      return 1;
    } else if (led == twoMinuteRemainderLed) {
      return 2;
    } else if (led == threeMinuteRemainderLed) {
      return 3;
    } else if (led == fourMinuteRemainderLed) {
      return 4;
    } else {
      return 12;
    }
  }

  /**
   * Display an Error pop-up
   *
   * @param msg The message to display
   */
  private void showError(String msg) {
    Alert alert = new Alert(AlertType.ERROR);
    alert.setTitle("Error");
    alert.setHeaderText("An error has occurred.");
    alert.setContentText(msg);
    alert.showAndWait();
  }

}
