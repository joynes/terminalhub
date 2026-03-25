## Tmux Scroll Problem

The app uses the Termux terminal widget, but it is not the full Termux app.

It is:
- your own Android app
- using the Termux terminal view
- connected over SSH
- running tmux on the remote machine

That means scrolling has 3 different layers:
- Android touch gestures
- the Termux terminal widget
- tmux itself

Why this is hard:
- In a normal shell, swipe can scroll local terminal history.
- In tmux, the terminal widget often does not scroll local history.
- Instead, it may send mouse-wheel events or key events into tmux.
- tmux usually expects scrolling to happen in copy mode.

So the swipe gesture has been interpreted differently depending on state.

That caused symptoms like:
- raw escape characters showing in the terminal
- old commands appearing instead of scrolling
- swipe working in one direction but not the other
- selection mode scrolling working, but normal swipe not working

The core problem in simple words:

Swipe was going through too many layers, and each layer had a different idea of what "scroll" means.

What was changed:
- For tmux sessions, swipe handling was changed to stop depending on default Termux behavior.
- Instead, the app now tries to tell tmux directly to scroll its own history.

Simple before/after:
- Before: swipe = Android -> Termux widget -> maybe mouse events -> maybe tmux reacts
- Now: swipe in tmux = app directly asks tmux to scroll

Important:
- This only affects the app/project.
- No local machine tmux config should be changed anymore.

