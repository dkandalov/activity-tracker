### Activity Tracker
This is proof-of-concept plugin for IntelliJ IDEs to track and record user activity.
Currently the main feature is recording user activity into csv files.

To install the plugin use ``IDE Settings -> Plugins -> Browse Repositories``
or download [latest zip](https://github.com/dkandalov/activity-tracker/blob/master/activity-tracker-plugin.zip)
and use ``IDE Settings -> Plugins -> Install plugin from disk...``.

To use the plugin see "Activity tracker" widget in IDE statusbar.


### Why?
The main idea is to mine recorded data for interesting user or project-specific insights,
e.g. time spent in each part of project or editing/browsing ratio.
If you happen to use the plugin and find interesting way to analyze data, get in touch on
[twitter](https://twitter.com/dmitrykandalov) or [GitHub](https://github.com/dkandalov/activity-tracker/issues).


### Help
#### To open plugin popup menu:
 - click on "Activity tracker" widget in IDE statusbar or
 - invoke "Activity Tracker Popup" action (``ctrl+alt+shift+O`` shortcut)

#### Popup menu actions:
 - **Start/stop Tracking** - activate/deactivate recording of IDE events.
 Events are written into ``ide-events.csv`` file located in predefined path.
 This file is referred to as "current log".
 - **Current Log**
    - **Show Stats** - analyse current log and opens toolwindow which shows time spent editing each file.
		There are plans to add more types of analysis, but currently that's all there is to it.
		You're more than welcome to suggest analysis or send some code.
    - **Open in IDE** - open current log file in IDE editor.
    - **Open in File Manager** - open current lof in file manager.
        This might be a good to navigate to log file location path.
    - **Roll Tracking Log** - rename current log file by adding date postfix to it.
        The intention is to keep previous data and clear current log.
    - **Clear Tracking Log** - remove all data from current log.
 - **Settings**
    - **Track IDE Action** - enable capturing IDE actions.
    - **Poll IDE State** - enable polling IDE state every 1 second even if there is activity.
        Enable this to get more accurate data about time spent in/outside of IDE.
    - **Track Keyboard** - enable tracking UI keyboard events. **Beware!**
        If you enter sensitive information (like passwords), it might be captured and stored in current log file.
    - **Track Mouse** - enable tracking UI mouse click events.

#### Log file columns:
 - **timestamp** - time of event in ``yyyy/MM/dd kk:mm:ss.SSS`` format.
 - **user name** - current user name. The intention is to be able to merge logs from several users.
 - **event type**, **event data** - depends on type of captured event.
    - for IDE actions: event type = ``Action``, event data = ``[action id]`` (e.g. ``Action,EditorUp``);
                       or event type = ``VcsAction`, event data = ``[Push|Update|Commit]``.
    - for IDE polling events: event type = ``IdeState``, event data = ``[Active|Inactive|NoProject]``,
      where ``Inactive`` means IDE doesn' have focus, ``NoProject`` mean all projects are closed.
    - for keyboard events: event type = ``KeyEvent``, event data = ``[keyChar]:[keyCode]:[modifiers]``
      (see [AWT KeyEvent](https://docs.oracle.com/javase/7/docs/api/java/awt/event/KeyEvent.html)).
    - for mouse events: event type = ``MouseEvent``, event data = ``[button]:[modifiers]``
      (see [AWT MouseEvent](https://docs.oracle.com/javase/7/docs/api/java/awt/event/MouseEvent.html)).
 - **project name** - name of active project
 - **focused component** - can be toolwindow id, ``Editor``, ``Dialog`` or ``Popup``.
 - **current file** - absolute path to file open in editor (even when editor has no focus).
 - **PSI path** - [PSI](http://www.jetbrains.org/intellij/sdk/docs/basics/architectural_overview/psi_elements.html)
                  path to currently selected element, format ``[parent]::[child]``.
 - **editor line** - caret line number in Editor.
 - **editor column** - caret column number in Editor.


#### Example of log file
```
2015/12/31 01:07:53.300,dima,MouseEvent,1:16,activity-tracker,Plugins,/path/to/activity-tracker/plugin.groovy,,20,0
2015/12/31 01:07:53.419,dima,KeyEvent,65535:16:1,activity-tracker,Plugins,/path/to/activity-tracker/plugin.groovy,,20,0
2015/12/31 01:07:53.555,dima,Action,HideActiveWindow,activity-tracker,Plugins,/path/to/activity-tracker/plugin.groovy,,20,0
2015/12/31 01:07:53.556,dima,KeyEvent,27:27:1,activity-tracker,Popup,/path/to/activity-tracker/plugin.groovy,,20,0
2015/12/31 01:07:54.293,dima,IdeState,,activity-tracker,Editor,/path/to/activity-tracker/plugin.groovy,,20,0
2015/12/31 01:07:54.306,dima,KeyEvent,65535:157:4,activity-tracker,Editor,/path/to/activity-tracker/plugin.groovy,,20,0
2015/12/31 01:07:55.290,dima,IdeState,,activity-tracker,Editor,/path/to/activity-tracker/plugin.groovy,,20,0
2015/12/31 01:07:56.296,dima,IdeState,,,,,,-1,-1
2015/12/31 01:07:57.291,dima,IdeState,,,,,,-1,-1
```

### Contributing
The best way to run plugin from source code is to use [LivePlugin](https://github.com/dkandalov/live-plugin).

The most interesting thing to look into at the moment is analysis of recorded data.
All suggestions and code (even if it's not JVM language) are welcome.
If you have a question, feel free to create an issue.
