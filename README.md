[![Build Status](https://travis-ci.org/dkandalov/activity-tracker.svg?branch=master)](https://travis-ci.org/dkandalov/activity-tracker)

## Activity Tracker
This is proof-of-concept plugin for IntelliJ IDEs to track and record user activity.
Currently the main feature is recording user activity into csv files.

To install the plugin use `IDE Settings -> Plugins -> Browse Repositories`
or download [latest zip](https://github.com/dkandalov/activity-tracker/blob/master/activity-tracker-plugin.zip)
and use `IDE Settings -> Plugins -> Install plugin from disk...`.

To use the plugin see "Activity tracker" widget in IDE statusbar.

<img src="https://raw.githubusercontent.com/dkandalov/activity-tracker/master/screenshot.png" alt="screenshot" title="screenshot" align="center"/>


## Why?
The main idea is to mine recorded data for interesting user or project-specific insights,
e.g. time spent in each part of project or editing/browsing ratio.
Especially it might be interesting to try some unsupervised machine learning on it.
If you happen to use the plugin and find interesting ways to analyze data, feel free to get in touch on
[twitter](https://twitter.com/dmitrykandalov) or [GitHub](https://github.com/dkandalov/activity-tracker/issues).


## Help
To open plugin popup menu:
 - click on "Activity tracker" widget in IDE statusbar or
 - invoke "Activity Tracker Popup" action (`ctrl+alt+shift+O` shortcut)

### Popup menu actions
 - **Start/Stop Tracking** - activate/deactivate recording of IDE events.
 Events are written into `ide-events.csv` file located in predefined path.
 This file is referred to as "current log".
 - **Current Log**
    - **Show Stats** - analyse current log and open toolwindow which shows time spent editing each file.
		There are plans to add more types of analysis, but currently that's all there is to it.
		You're more than welcome to suggest analysis or send some code.
    - **Open in IDE** - open current log file in IDE editor.
    - **Open in File Manager** - open current lof in file manager
        (can be useful to navigate to log file location path).
    - **Roll Tracking Log** - rename current log file by adding date postfix to it.
        The intention is to keep previous data and clear current log.
    - **Clear Tracking Log** - remove all data from current log.
 - **Settings**
    - **Track IDE Action** - enable capturing IDE actions.
    - **Poll IDE State** - enable polling IDE state (every 1 second) even if there is no activity.
        Enable this option to get more accurate data about time spent in/outside of IDE.
    - **Track Keyboard** - enable tracking keyboard events. __**Beware!**__
        If you enter sensitive information (like passwords), it might be captured and stored in current log file.
    - **Track Mouse** - enable tracking mouse click events.
      Note that because of high volume of mouse move and wheel events, they are logged at most every 250 milliseconds.

### Log file format
The event log file is written as [csv RFC4180](https://tools.ietf.org/html/rfc4180) in UTF-8 encoding.

 - **timestamp** - time of event in `yyyy-MM-dd'T'HHmmss.SSSZ` format
   (see [createDateTimePrintFormat() method](https://github.com/dkandalov/activity-tracker/blob/6ca1342e8c71c96f5f7a1c52095c61317cc78650/src/main/activitytracker/TrackerEvent.groovy#L109-L109)).
   In plugin version 0.1.3 it was [ISO-8601 extended format](https://docs.oracle.com/javase/8/docs/api/java/time/format/DateTimeFormatter.html#ISO_OFFSET_DATE_TIME).
   In plugin version 0.1.2 it was `yyyy/MM/dd kk:mm:ss.SSS` format.
 - **user name** - current user name. The intention is to be able to merge logs from several users.
 - **event type**, **event data** - content depends on type of captured event.
    - **IDE actions**: event type = `Action`, event data = `[action id]` (e.g. `Action,EditorUp`);
                       or event type = `VcsAction`, event data = `[Push|Update|Commit]`;
                       or event type = `CompilationFinished`, event data = `[amount of errors during compilation]`.
    - **IDE polling events**: event type = `IdeState`, event data = `[Active|Inactive|NoProject]`,
      where `Inactive` means IDE doesn' have focus, `NoProject` mean all projects are closed.
    - **keyboard events**: event type = `KeyEvent`, event data = `[keyChar]:[keyCode]:[modifiers]`
      (see [AWT KeyEvent](https://docs.oracle.com/javase/7/docs/api/java/awt/event/KeyEvent.html)).
    - **mouse events**: event type = `MouseEvent`, event data can be
        - `click:[button]:[clickCount]:[modifiers]`
        - `move:[x]:[y]:[modifiers]`
        - `wheel:[wheelRotation]:[wheelModifiers]`

      (see java [MouseEvent](https://docs.oracle.com/javase/7/docs/api/java/awt/event/MouseEvent.html)
      and [MouseWheelEvent](https://docs.oracle.com/javase/7/docs/api/java/awt/event/MouseWheelEvent.html) for details).
 - **project name** - name of active project.
 - **focused component** - can be `Editor`, `Dialog`, `Popup` or toolwindow id (e.g. `Version Control` or `Project`).
 - **current file** - absolute path to file open in editor (even when editor has no focus).
 - **PSI path** - [PSI](http://www.jetbrains.org/intellij/sdk/docs/basics/architectural_overview/psi_elements.html)
                  path to currently selected element, format `[parent]::[child]`.
                  Empty value if file was not parsed by IDE (e.g. plain text file).
 - **editor line** - caret line number in Editor.
 - **editor column** - caret column number in Editor.
 - **task/change list name** - name of the current task (`Main Menu -> Tools -> Tasks & Contexts`) 
                               or current VCS change list name if "Task Management" plugin is not installed.


### Example of log file
```
2015-12-31T17:42:30.171Z,dima,IdeState,Active,activity-tracker,Editor,/path/to/jdk/src.zip!/java/awt/AWTEvent.java,AWTEvent::isConsumed,450,8,
2015-12-31T17:42:30.35Z,dima,Action,EditorLineEnd,activity-tracker,Editor,/path/to/jdk/src.zip!/java/awt/AWTEvent.java,AWTEvent::isConsumed,450,8,
2015-12-31T17:42:30.351Z,dima,KeyEvent,97:79:8,activity-tracker,Editor,/path/to/jdk/src.zip!/java/awt/AWTEvent.java,AWTEvent::isConsumed,450,24,
2015-12-31T17:42:30.566Z,dima,Action,EditorLineStart,activity-tracker,Editor,/path/to/jdk/src.zip!/java/awt/AWTEvent.java,AWTEvent::isConsumed,450,24,
2015-12-31T17:42:30.568Z,dima,KeyEvent,97:85:8,activity-tracker,Editor,/path/to/jdk/src.zip!/java/awt/AWTEvent.java,AWTEvent::isConsumed,450,8,
2015-12-31T17:42:30.998Z,dima,KeyEvent,65535:157:4,activity-tracker,Editor,/path/to/jdk/src.zip!/java/awt/AWTEvent.java,AWTEvent::isConsumed,450,8,
2015-12-31T17:42:31.17Z,dima,IdeState,Active,activity-tracker,Editor,/path/to/jdk/src.zip!/java/awt/AWTEvent.java,AWTEvent::isConsumed,450,8,
2015-12-31T17:42:32.169Z,dima,IdeState,Inactive,,,,,-1,-1,
2015-12-31T17:42:33.168Z,dima,IdeState,Inactive,,,,,-1,-1,
```

### How to use log file?
This is up to you.
The main purpose of this plugin is to record data.
It's similar to collecting IDE statistics usage (which you might be sending to JetBrains) except that you own the data.


## Contributing
The most interesting thing is analysis of recorded data.
All suggestions and code are welcome (even if it's not a JVM language, e.g. a python snippet).
If you have a question, feel free to create an issue.

Working on the plugin:
 - to edit code, open project in IJ IDEA importing gradle configuration 
 - to build use `./gradlew buildPlugin` task which will create `build/distributions/activity-tracker-plugin.zip` files
 - to run use `./gradlew runIde` task
 - alternatively, you can use [LivePlugin](https://github.com/dkandalov/live-plugin) 
 as an [entry point](https://github.com/dkandalov/live-plugin/wiki/Liveplugin-as-an-entry-point-for-standard-plugins)
 for this plugin code.
