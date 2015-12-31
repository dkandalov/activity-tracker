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
To open plugin popup menu:
 - click on "Activity tracker" widget in IDE statusbar or
 - invoke "Activity Tracker Popup" action (``ctrl+alt+shift+O`` shortcut)

Popup menu actions:
 - ``Start/stop Tracking`` activates/deactivates recording of IDE events.
 Events are written into ``ide-events.csv`` file located in predefined path.
 This file is referred to as "current log".
 - ``Current Log``
    - ``Show Stats`` analyses current log and opens toolwindow which shows time spent editing each file.
     There is a plan to add more types of analysis, but currently that's all there is to it.
     You're more than welcome to suggest analysis or send some code.
    - ``Open in IDE`` opens current log file in IDE editor.
    - ``Open in File Manager`` opens current lof in file manager.
    This might be a good to navigate to log file location path.
    - ``Roll Tracking Log`` rename current log file by adding date postfix to it.
    The intention is to keep previous data and clear current log.
    - ``Clear Tracking Log`` removes all data from current log.
 - ``Settings``
    - ``Track IDE Action`` enables capturing IDE actions
    - ``Poll IDE State`` enables polling IDE state every 1 second even if there is activity.
    Enable this to get more accurate data about time spent in/outside of IDE.
    - ``Track Keyboard`` enable tracking UI keyboard events. **Beware!**
    If you enter sensitive information like passwords, they might be captured and stored in current log file.
    like passwords
    - ``Track Mouse`` enables tracking UI mouse click events.


Log file format:
 - TODO
Example of log file:
```
```