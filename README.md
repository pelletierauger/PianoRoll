# PianoRoll
SuperCollider class to create a Piano Roll GUI.

No longer under active development, but it should still work under recent versions of SuperCollider.

# Requirements

Put the `PianoRoll` directory in your SuperCollider `Extensions` directory as normal. For example, on Linux, this is usually `~/.local/share/SuperCollider/Extensions/`. You'll also need to put these in it as well:

* [Sequence](https://github.com/defaultxr/Sequence)
* [Keymap](https://github.com/defaultxr/Keymap)

# How to Use

Create a PianoRoll window:

```supercollider
(
var win, pr;
win = Window("Seq");
pr = PianoRoll(win, win.view.bounds).resize_(5);
~pr = pr;
win.front;
)
```

To use the GUI:

* Double click in the grid to make a note.
* Right click on a note to delete it.
* Click on the rightmost 5 pixels of a note and drag to adjust its duration.
* Left click on a note to select it.
* Select multiple notes simultaneously by clicking and dragging on the grid. Notes whose beginning point are within the box will be selected. Blue notes are selected; red are not.
* Press the `delete` key on your keyboard to delete all selected notes.
* Press `ctrl+1` to zoom in, and `ctrl+3` to zoom out.
* Press `i` to halve quantization, allowing you more fine-grained control over resizing or positioning a note. Press `o` to double quantization.
* By default, the duration of the sequence is the time that the last note ends at rounded up to the nearest integer, however you can manually set its length by right clicking on the top bar. The black numbers show the length of the sequence.
* Press `space` to play the pattern, and `space` again to stop it if it's playing.
* Press `shift+space` to loop the pattern. Note that changes made to the pattern while it's playing will not take effect until the next loop starts.
* Press `ctrl+h` or `?` to get an overview of the keymap.

The numbers in the sidebar are MIDI note numbers.

The duration for new notes is set to be the duration of the last note you edited or added. You can manually set the duration of new notes by setting the `PianoRoll`'s `newNoteSustain` instance variable:

```supercollider
~pr.newNoteSustain_(0.5); // set new notes to be 0.5 beats in duration
~pr.newNoteSustain_(\last); // set new notes to be the same duration as the last note edited (default behavior)
```

To get the Sequence of Events from the PianoRoll:

```supercollider
~pr.sequence;
```

Sequence is a class designed to make it easy to hold and manipulate a set of Events. You'll probably want to convert the Sequence into a Pattern:

```supercollider
~pr.sequence.asPattern;
```

To change the "proto-Event" for the Sequence (i.e. to change the `instrument` of all the notes without setting it for each one individually):

```supercollider
~pr.sequence.protoEvent_((instrument:\foo))
```

You can of course override the Sequence's proto-Event on a per-note basis:

```supercollider
~pr.sequence.list[0] = (beat:0, instrument:\bar, midinote:69, dur:3);
~pr.refresh;
```

Note that if you edit a note manually like this, you need to manually refresh the `PianoRoll` afterwards to see the changes. You can do that with the `refresh` method, or just by clicking on the GUI.

You'll also need to take care to make sure all the Events you add have `beat` and `midinote` keys. `beat` is used by Sequence to determine the start point of the Event within the Sequence.

Additionally, be aware that the Sequence's list of notes is in order from least to most recently added, NOT by the note's start beat within the Sequence. To get the index of a note in the PianoRoll, double click it.

If you have any questions or problems, feel free to open an issue.
