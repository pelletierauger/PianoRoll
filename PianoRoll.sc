/*
PianoRoll - a piano roll for SuperCollider
TODO:
* make sure ySpec can be \freq, \instrument, etc.
*/

PianoRoll : SCViewHolder {
    var <bounds;
    var <translate;
    var <grid=true;
    var <>selectedStrokeColor;
    var <>highlightedStrokeColor;
    var <ySpec=\midinote;
    var <>propDisplay='midinote';
    var <vSize = 20; // size of each row in pixels
    var <vquant;
    var <beatSize = 80; // size of each beat in pixels
    var <beatQuant = 0.25; // x quantizing
    var <sidebarWidth = 60, <topbarHeight = 60;
    var <eventEditor;
    var <noteVelButton;
    var <playButton;
    var <pdefSymbol;

    var <>selected; // list of selected notes
    var <>highlighted; // list of highlighted notes
    var clickedOn, mouseDownPoint, mouseDownButton, dragAction=nil; // mouse actions context info
    var selectionRect, ghosts;
    var tview, uv;
    var <sequence;
    var newNoteSustain=\last;
    var <>messagequeue, <>keymap;
    var scrollCount = 0;

    *initClass {
        Spec.add(\midinote, [0, 127, \lin, 1, 60]); // i have no idea why the default step for \midinote is 0...
    }

    *new {
        | parent bounds sequence |
        ^super.new.init(parent, bounds, sequence);
    }

    init {
        | parent argbounds argsequence |
        bounds = argbounds ?? {Rect(0, 0, 500, 500)};
        bounds = bounds.asRect;
        sequence = argsequence ?? { Sequence.new };
        vquant = 1; // vertical quant - remove this? - FIX
        ySpec = ySpec.asSpec;//ControlSpec(0, 127, \lin, 1, 64);
        translate = Point(0, 0);
        selected = List.new;
        selectedStrokeColor = Color.white;
        highlighted = List.new;
        highlightedStrokeColor = Color.green;
        messagequeue = [];
        uv = UserView(parent, bounds).background_(Color.gray(0.65));
        tview = UserView(parent, bounds).acceptsMouse_(false);
        view = UserView(parent, bounds).layout_(StackLayout(tview, uv).mode_(\stackAll));
        tview.drawFunc_({
            | view |
            Pen.translate(sidebarWidth+translate.x, topbarHeight+translate.y);
            if(selectionRect.notNil, {
                Pen.fillColor_(Color.gray(0.5, 0.2));
                Pen.strokeColor_(Color.gray(0.5, 0.4));
                Pen.addRect(selectionRect);
                Pen.draw(3);
            });
            if(ghosts.notNil, {
                Pen.color_(Color.red(1, 0.5));
                ghosts.do({
                    | note n |
                    if(note.notNil, {
                        var rect = this.noteToRect(note);
                        Pen.addRect(rect);
                        Pen.draw(3);
                    });
                });
            });
        });
        uv.drawFunc_({
            | view |
            if(sidebarWidth > 0, {
                var vOffset = (translate.y/vSize).min(0).floor.abs;
                var sArray = (ySpec.minval, if(ySpec.step==0, ySpec.guessNumberStep, ySpec.step) .. ySpec.maxval);
                (view.bounds.height/vSize).floor.do({ // numbers
                    | n |
                    var ypos = (n*vSize)+topbarHeight;
                    var note = (ySpec.maxval-(vOffset+n));
                    var blackKeys = true;
                    Scale.major.degrees.do({
                        |i|
                        if ((note % 12) == i, {blackKeys = false});
                    });
                    // maj;
                    Pen.fill;

                    if (blackKeys == true,
                        {Pen.color_(Color.gray(0.4))},
                        {Pen.color_(Color.gray(0.45))}
                    );
                    Pen.addRect(Rect(sidebarWidth, ypos, view.bounds.width-sidebarWidth, vSize*2));
                });
                Pen.stroke;
            });
            if(grid, { // draw grid lines
                var numlines = ((view.bounds.width)/(beatSize*beatQuant));
                numlines.floor.do({ // vertical lines
                    | n |
                    var xpos = sidebarWidth+(n*(beatSize*beatQuant));
                    if(((n*(beatSize*beatQuant))-translate.x)%beatSize == 0, {
                        Pen.color_(Color.gray(0.25, 0.5*2));
                    }, {
                        Pen.color_(Color.gray(0.25, 0.125*2));
                    });
                    Pen.line(Point(xpos, translate.y.max(0)), Point(xpos, view.bounds.height));
                    Pen.stroke;
                });
                Pen.color_(Color.gray(0.25, 0.125*2));
                ((view.bounds.height-(translate.y).min(0))/vSize).floor.do { // horizontal lines
                    | n |
                    var ypos = translate.y+((1+n)*vSize);
                    Pen.line(Point(translate.x.max(0), ypos), Point(view.bounds.width, ypos));
                };
                Pen.stroke;
            });
            Pen.translate(sidebarWidth+translate.x, topbarHeight+translate.y);
            sequence.rawList.do {
                | note n |
                if(note.notNil, {
                    var rect = this.noteToRect(note);
                    var toNote = {|num|
                        var notes = [
                            ["C"], ["C#", "Db"], ["D"], ["D#", "Eb"],
                            ["E"], ["F"], ["F#", "Gb"], ["G"] ,
                            ["G#", "Ab"], ["A"], ["A#", "Bb"], ["B"]];
                        var oct = (num / 12).floor;
                        var noteName = notes[num - (oct * 12)];
                        noteName.collect({|i, item| i ++ (oct - 1)});
                    };
                    var normVel = note[\velocity] / 127;
                    var displayText;
                    if (this.propDisplay == 'midinote', {
                        displayText = toNote.(note.midinote)[0];
                    }, {
                        displayText = note[this.propDisplay].asString;
                    });
                    // Pen.fillColor_(Color.new(1, 1 - normVel, 1 - normVel));
                    Pen.fillColor_(note.color.blend(Color.white, 1 - normVel));
                    Pen.strokeColor_(case(
                        { this.isSelected(note) }, {
                            selectedStrokeColor;
                        },
                        { this.isHighlighted(note) }, {
                            highlightedStrokeColor;
                        },
                        true, {
                            Color.black;
                        },
                    ));
                    /*                    Pen.width_(case(
                    { this.isSelected(note) }, {
                    3;
                    },
                    { this.isHighlighted(note) }, {
                    3;
                    },
                    true, {
                    2;
                    },
                    ));*/
                    Pen.addRect(rect);
                    Pen.draw(3);
                    Pen.color_(Color.black);
                    // ++ " / " ++ (note[\velocity]

                    Pen.stringCenteredIn(displayText, rect, Font("Inconsolata", 16));
                    Pen.stroke;
                });
            };
            Pen.translate((sidebarWidth+translate.x).neg, (topbarHeight+translate.y).neg);
            // Draw the topbar
            if(topbarHeight > 0, {
                var hOffset = (translate.x.abs/beatSize).max(0).floor;
                Pen.color_(Color.gray(0.35));
                Pen.addRect(Rect(0, 0, view.bounds.width, topbarHeight));
                Pen.fill;
                (view.bounds.width/beatSize).ceil.do({
                    | n |
                    var xpos = ((n+hOffset)*beatSize)+sidebarWidth+translate.x;
                    var num = (n+hOffset);
                    Pen.color_(if(num < this.sequence.dur, Color.white, Color.grey(0.65))); // show dur of sequence
                    Pen.stringCenteredIn(num.asString, Rect(xpos, 20, beatSize, topbarHeight));
                });
                Pen.stroke;
            });
            // Draw the sidebar
            if(sidebarWidth > 0, {
                var vOffset = (translate.y/vSize).min(0).floor.abs;
                var sArray = (ySpec.minval, if(ySpec.step==0, ySpec.guessNumberStep, ySpec.step) .. ySpec.maxval);
                var toNote = {|num|
                    var notes = [
                        ["C"], ["C#", "Db"], ["D"], ["D#", "Eb"],
                        ["E"], ["F"], ["F#", "Gb"], ["G"] ,
                        ["G#", "Ab"], ["A"], ["A#", "Bb"], ["B"]];
                    var oct = (num / 12).floor;
                    var noteName = notes[num - (oct * 12)];
                    noteName.collect({|i, item| i ++ (oct - 1)});
                };
                // Pen.color_(Color.red(0.5));
                Pen.color_(Color.gray(0.35));

                // if (note % 2 == 0, {Pen.color_(Color.gray(0.25))});
                Pen.addRect(Rect(0, 0, sidebarWidth, view.bounds.height));
                Pen.fill;
                Pen.color_(Color.white);
                (view.bounds.height/vSize).floor.do({ // numbers
                    | n |
                    var ypos = (n*vSize)+topbarHeight;
                    var note = (ySpec.maxval-(vOffset+n));
                    Pen.fill;
                    Pen.color_(Color.white);
                    Pen.stringCenteredIn((toNote.(note)[0]).asString, Rect(0, ypos, sidebarWidth, vSize), Font("Inconsolata", 16));
                    // if (n % 2 == 0, {Pen.color_(Color.gray(0.15))}, {Pen.color_(Color.gray(0.35))});
                    // Pen.addRect(Rect(sidebarWidth, ypos, view.bounds.width-sidebarWidth, vSize*2));
                });
                Pen.stroke;
            });
            // messages
            messagequeue = messagequeue.select({
                | item |
                (item[\time]-(Date.localtime.rawSeconds-item[\added]))>0;
            });
            messagequeue.select({|x|x[\text].notNil or: { x[\obj].notNil }}).reverse.do({
                | item num |
                var text = if(item[\text].notNil, {
                    item[\text];
                }, {
                    item[\obj].cs;
                });
                Pen.stringAtPoint(text, (sidebarWidth+5)@(view.bounds.height-(15*(num+2))-10), Font(), item[\color]??Color.white);
            });
        });
        keymap = Keymap((
            'C-1': \zoomIn,
            'C-3': \zoomOut,
            'i': \halveQuant,
            'o': \doubleQuant,
            \space: \playPause,
            'S-space': Message(this, \playPause, [nil, nil, true]),
            \delete: \deleteSelected,
            // \enter: {
            // 	| this |
            // 	this.edit(if(this.selected.size==0, nil, this.selected));
            // },
            ['C-h', '?']: \help,
        ));
        uv.keyDownAction_({
            | view char modifiers unicode keycode key |
            var res = keymap.keyDown(Keymap.stringifyKey(modifiers, keycode));
            keycode.postln;
            // modifiers.postln;
            // unicode.postln;
            // char.postln;
            // ("key:" ++ key).postln;
            case(
                { res.isKindOf(Function) }, {
                    res.value(this);
                },
                { res.isKindOf(Symbol) }, {
                    Message(this, res, []).value;
                },
                { res.isKindOf(Message) }, {
                    res.value;
                },
            );
            case (
                {keycode == 51 }, {
                    this.deleteSelected;
                },{key == 65 }, {
                    if (modifiers == 1048576, {
                        // Select All
                        sequence.rawList.do {
                            | note n |
                            this.select(note);
                        };
                        this.refresh;
                    });
                },{keycode == 2 }, {
                    if (modifiers == 1048576, {
                        // Select All
                        sequence.rawList.do {
                            | note n |
                            this.deselect(note);
                        };
                        this.refresh;
                    });
                },{keycode == 12 }, {
                    this.doubleQuant;
                },{keycode == 13 }, {
                    this.halveQuant;
                },{keycode == 17 }, {
                    eventEditor.doAction;
                }, {keycode == 15 }, {
                    var str = this.selected[0].asString;
                    if (this.selected[0].isKindOf(Event), {
                        var st = str.replace("( ", "");
                        st = st.replace(" )", "");
                        eventEditor.string = st;
                    });
                }, {keycode == 126 }, {
                    if (modifiers == 2359296, {
                        Message(this, \increaseVelSelected, []).value;
                    }, {
                        if (modifiers == 2228224,
                            {Message(this, \raiseSelectedOct, []).value;},
                            {Message(this, \raiseSelected, []).value;});
                    });
                }, {keycode == 125 }, {
                    if (modifiers == 2359296, {
                        Message(this, \decreaseVelSelected, []).value;
                    }, {
                        if (modifiers == 2228224,
                            {Message(this, \lowerSelectedOct, []).value;},
                            {Message(this, \lowerSelected, []).value;});

                    });
            });
        });
        uv.mouseDownAction_({
            | view x y modifiers buttonNumber clickCount |
            case(
                { x <= sidebarWidth and: { y <= topbarHeight } }, { // clicked in the top left bit
                    if(clickCount == 2, {
                    });
                },
                { x <= sidebarWidth }, { // clicked on the sidebar
                    // "sidebar".postln;
                },
                { y <= topbarHeight }, { // clicked on the top bar
                    if(buttonNumber == 1, { // right click to set dur...
                        var beat = this.pointToBeat((x@y));
                        this.sequence.dur_(if(beat < 1, nil, beat.round));
                    });
                },
                true, { // clicked in the note area
                    clickedOn = this.getAtPoint(x@y);
                    mouseDownPoint = x@y;
                    mouseDownButton = buttonNumber;
                    switch(buttonNumber,
                        0, {
                            switch(clickCount,
                                1, { // left click to select notes.
                                    if(clickedOn.size > 0, { // clicked on something
                                        var nrect = this.noteToRect(clickedOn[0]);
                                        dragAction = nil;
                                        if(( buttonNumber == 0 ) and: { (this.untranslatePoint(nrect.rightBottom) - (x@y)).x < 5 }, {
                                            dragAction = \resize;
                                        });
                                    });
                                    if(modifiers.isShift, {
                                        clickedOn.do({
                                            | item |
                                            if(this.isSelected(item), {
                                                this.deselect(item);
                                            }, {
                                                this.select(item);
                                            });
                                        });
                                    }, {
                                        if(clickedOn.size == 0, {
                                            this.deselectAll;
                                        }, {
                                            if(this.isSelected(clickedOn[0]).not, {
                                                this.deselectAll;
                                            });
                                            if(selected.size == 0, {
                                                clickedOn.do({
                                                    | item |
                                                    this.select(item);
                                                });
                                            });
                                        });
                                    });
                                },
                                2, { // double left-click to make a note
                                    if((clickedOn.size > 0) and: { selected.size == 1 }, {
                                        selected[0].deepCopy.play;
                                        this.message("Event #"++this.sequence.list.indexOf(selected[0]).asString);
                                    }, {
                                        this.add((
                                            midinote: (this.pointToY(x@y)+(vquant/2)).round(vquant),
                                            sustain: this.newNoteSustain,
                                            beat: (this.pointToBeat(x@y)-(beatQuant/2)).round(beatQuant),
                                            velocity: 63,
                                            color: Color.red
                                        ));
                                    });
                                },
                            );
                        },
                        1, {
                            this.getAtPoint(x@y).do({
                                | item |
                                this.delete(item);
                            });
                        },
                    );
                },
            );
            this.refresh;
        });
        uv.mouseMoveAction_({
            | uvw x y modifiers |
            switch(mouseDownButton,
                0, {
                    if(clickedOn.size == 0, { // draw the selection rect if we didn't click on anything.
                        var untranslatedRect = Rect.fromPoints(mouseDownPoint, x@y);
                        selectionRect = Rect.fromPoints(
                            this.translatePoint(mouseDownPoint),
                            this.translatePoint(x@y),
                        );
                    }, { // if we clicked on something, move or resize all selected notes
                        if(dragAction == \resize, { // FIX: just find the % of the resized note and apply it to all notes \sustain and \beat
                            var drag, c_beats, start, end, oldsize, newsize;
                            drag = ((x - mouseDownPoint.x)/beatSize).round(beatQuant);
                            c_beats = selected.collect(_[\beat]);
                            start = c_beats.reduce(\min);
                            end = selected.sortBy(\beat);
                            end = end.reject({
                                | e |
                                e[\beat] < end.last[\beat];
                            });
                            end = end.collect(_[\sustain]).reduce(\max) + end[0][\beat];
                            oldsize = end - start;
                            newsize = (end + drag) - start;
                            ghosts = selected.deepCopy;
                            ghosts.do({
                                | item |
                                item[\sustain] = (item[\sustain] * (newsize/oldsize));
                                item[\beat] = ((item[\beat]-start)*(newsize/oldsize))+start;
                            });
                        }, {
                            ghosts = selected.deepCopy;
                            ghosts.do({
                                | item |
                                var diff = ((x@y) - mouseDownPoint);
                                item[\beat] = item[\beat] + (diff.x/beatSize).round(beatQuant);
                                item[\midinote] = item[\midinote]?60 - (diff.y/vSize).round;
                            });
                        });
                    });
                    tview.refresh;
                },
                /*                1, {
                this.getAtPoint(x@y).do({
                | item |
                this.delete(item);
                });
                uv.refresh;
                },*/
                1, {
                    if (scrollCount % 4 == 0, {
                        var newtrans = this.translate;
                        var diff = ((x@y) - mouseDownPoint);
                        newtrans.x = (newtrans.x + (diff.x.sign*(beatSize*beatQuant)));
                        newtrans.y = (newtrans.y + (diff.y.sign*vSize));
                        this.translate_(newtrans);
                    });
                    scrollCount = scrollCount + 1;
                }
            );
        });
        uv.mouseUpAction_({
            | uvw x y modifiers |
            if(selectionRect.notNil, { // select items in the rect on mouse up
                var untranslatedRect = Rect.fromPoints(
                    this.untranslatePoint(selectionRect.leftTop),
                    this.untranslatePoint(selectionRect.rightBottom),
                );
                var items = this.getInRect(untranslatedRect);
                items.do({
                    | item |
                    this.select(item);
                });
                selectionRect = nil;
            });
            if(ghosts.notNil, {
                var os = selected.deepCopy;
                var gh = ghosts.deepCopy;
                if(modifiers.isAlt.not, {
                    os.do({
                        | item |
                        this.delete(item);
                    });
                    this.deselectAll;
                });
                gh.do({
                    | item |
                    this.add(item);
                    this.select(item);
                });
                ghosts = nil;
            });
            this.refresh;
        });
        uv.mouseWheelAction_({
            | view x y modifiers xDelta yDelta |
            if(modifiers.isAlt, { // zoom
                beatSize = max(10, beatSize + (yDelta.sign * 10));
                this.refresh;
            }, {
                var newtrans = this.translate;
                // if (scrollCount % 256 == 0, {
                newtrans.x = (newtrans.x + (xDelta.sign*(beatSize*beatQuant)));
                newtrans.y = (newtrans.y + (yDelta.sign*vSize));

                this.translate_(newtrans);
                // scrollCount.postln;
                // });
                // scrollCount = scrollCount + 1;
            });
        });
        this.centerOn(\midinote.asSpec.default);
        eventEditor = TextField(parent, Rect(sidebarWidth*2-5, 5, 750, 30));
        eventEditor.font = Font("Inconsolata", 16);
        eventEditor.background = Color.gray(0.5, 0);
        eventEditor.stringColor = Color.white;
        eventEditor.focusColor = Color.gray(0,0);
        // o.border = Color.gray(0,0);
        eventEditor.string = "'velocity', 127";
        // a.action = {arg field; field.value.postln; };
        // a.action = {arg field; field.value.compile.value; };
        eventEditor.action = {
            arg field;
            var f = ("[" ++ field.value ++ "]").compile.value;
            if (f.isKindOf(Array), {
                if (f.size % 2 == 0, {
                    (f.size/2).do({
                        |j|
                        this.selected.do({|i|i[f[j*2]] = f[j*2+1]});
                    });
                    this.refresh;
                });
            });
            f.postln;
        };
        noteVelButton = Button(parent, Rect(60, 5, 50, 30))
        .states_([
            ["Note", Color.white, Color.gray(0.5)],
            ["Vel", Color.white, Color.gray(0.5)]
        ])
        .action_({ arg butt;
            if (butt.value == 0,{this.propDisplay = 'midinote'});
            if (butt.value == 1,{this.propDisplay = 'velocity'});
            this.refresh;
        });
        noteVelButton.font = Font("Inconsolata", 16);
        noteVelButton.focusColor = Color(0,0,0,1);
        noteVelButton.canFocus = false;
        playButton = Button(parent, Rect(5, 5, 50, 30))
        .states_([
            ["Play", Color.white, Color.gray(0.5)],
            ["Pause", Color.white, Color.gray(0.5)]
        ])
        .action_({ arg butt;
            if (butt.value == 0,{this.playPause(nil, nil, true);});
            if (butt.value == 1,{this.playPause});
            this.refresh;
        });
        playButton.font = Font("Inconsolata", 16);
        playButton.focusColor = Color(0,0,0,1);
        playButton.canFocus = false;
        pdefSymbol = (\__pianoRoll ++ (0..1e6).choose.asString.padLeft(6, "0")).asSymbol;
    }
    prNoteCompare {
        | event1 event2 |
        // FIX - will need to consider other stuff instead of \midinote when other ySpecs are supported by PianoRoll.
        ^(event1[\beat] == event2[\beat] and: { event1[\midinote] == event2[\midinote] });
    }
    message {
        | obj time raw=false |
        if(raw, {
            messagequeue = messagequeue ++ [(obj:obj, time:time?5, added:Date.localtime.rawSeconds)];
        }, {
            if(time.isNil, {
                time = 0;
                if(obj.isKindOf(String), {
                    time = obj.split($\n).size.max(5);
                }, {
                    time = 5;
                });
            });
            if(obj.notNil, {
                var ctime = Date.localtime.rawSeconds;
                messagequeue = messagequeue ++ if(obj.isKindOf(String), {
                    obj.split($\n).collect({
                        | obji |
                        [(text:obji, time:time, added:ctime)];
                    }).reduce('++');
                }, {
                    [(obj:obj, time:time, added:ctime)];
                });
            });
        });
        this.refresh;
    }
    bounds_ {
        | bounds |
        bounds = bounds;
        view.bounds = bounds;
        this.refresh;
    }
    newNoteSustain {
        if(newNoteSustain==\last, {
            if(this.sequence.list.size == 0, {
                ^1;
            }, {
                ^this.sequence.rawList.last[\sustain];
            });
        }, {
            ^newNoteSustain;
        });
    }
    newNoteSustain_ {
        | sustain |
        newNoteSustain = if(sustain.isKindOf(Number) or: { sustain == \last }, sustain, 1);
    }
    add {
        | note |
        sequence.add(note);
        this.refresh;
    }
    delete {
        | obj |
        this.deselect(obj);
        sequence.remove(obj);
        this.refresh;
    }
    del {
        | obj |
        ^this.delete(obj);
    }
    zoomIn {
        beatSize = beatSize + 10;
        this.refresh;
    }
    zoomOut {
        beatSize = beatSize - 10;
        this.refresh;
    }
    halveQuant {
        this.beatQuant_((this.beatQuant/2).clip(1/2048, 8));
    }
    doubleQuant {
        this.beatQuant_((this.beatQuant*2).clip(1/2048, 8));
    }
    deleteSelected {
        var csel = this.selected.deepCopy;
        csel.do({
            | item |
            this.delete(item);
        });
        this.deselectAll;
    }
    raiseSelected {
        this.selected.do({
            | item |
            item.midinote = min(item.midinote + 1, 127);
        });
        this.refresh;
    }
    lowerSelected {
        this.selected.do({
            | item |
            item.midinote = max(item.midinote - 1, 0);
        });
        this.refresh;
    }
    raiseSelectedOct {
        this.selected.do({
            | item |
            item.midinote = min(item.midinote + 12, 127);
        });
        this.refresh;
    }
    lowerSelectedOct {
        this.selected.do({
            | item |
            item.midinote = max(item.midinote - 12, 0);
        });
        this.refresh;
    }
    increaseVelSelected {
        this.selected.do({
            | item |
            item.velocity = min(item.velocity + 5, 127);
        });
        this.refresh;
    }
    decreaseVelSelected {
        this.selected.do({
            | item |
            item.velocity = max(item.velocity - 5, 0);
        });
        this.refresh;
    }
    edit {
        | obj |
        if(obj.isNil, {
            // edit the sequence
        }, {
            // edit the selected notes
        });
    }
    sequence_ {
        | seq |
        sequence = seq ?? { Sequence.new; };
        this.refresh;
    }
    getInRect { // returns the objects that are contained inside the provided Rect. don't provide a translated rect.
        | rect |
        var topYVal = this.pointToY(rect.top), botYVal = this.pointToY(rect.top+rect.height);
        ^sequence.eventsIn(this.pointToBeat(rect.left-this.translate.x), this.pointToBeat(rect.left+rect.width-this.translate.x))
        .select({
            | event |
            ((event[\midinote]?60 >= botYVal) and: { event[\midinote]?60 <= topYVal });
        });
    }
    getAtPoint { // returns the objects that contain the provided Point.
        | point |
        var beat = this.pointToBeat(point);
        var yVal = this.pointToY(point).ceil;
        // point.postcs;
        ^sequence.rawList.select({
            | event |
            var end = event[\beat] + event[\sustain];
            ((event[\beat] <= beat) and: { beat < end } and: { event[\midinote]?60 == yVal });
        });
    }
    isSelected {
        | obj |
        selected.do({
            | item |
            if(this.prNoteCompare(item, obj), {
                ^true;
            });
        });
        ^false;
    }
    select {
        | obj |
        if(this.isSelected(obj).not, { // FIX: don't select things that don't exist
            selected = selected.add(obj);
        });
    }
    deselect {
        | obj |
        selected = selected.reject(this.prNoteCompare(_, obj));
    }
    deselectAll {
        selected = [];
    }
    isHighlighted {
        | obj |
        highlighted.do({
            | item |
            if(this.prNoteCompare(item, obj), {
                ^true;
            });
        });
        ^false;
    }
    highlight {
        | obj |
        if(this.isHighlighted(obj).not, { // FIX: don't select things that don't exist
            highlighted = highlighted.add(obj);
        });
        this.refresh;
    }
    unhighlight {
        | obj |
        highlighted = highlighted.reject(this.prNoteCompare(_, obj));
        this.refresh;
    }
    unhighlightAll {
        highlighted = [];
    }
    notePlay {
        | event |
        if(event.notNil, {
            {this.highlight(event);}.defer(0);
            {this.unhighlight(event)}.defer(event[\dur]*(1/TempoClock.default.tempo));
        });
    }
    pointToBeat { // return the "beat" at the point. must be a non-translated point. - FIX
        | point | // should also accept numbers instead of just Points.
        var ix = if(point.isKindOf(Point), { point.x - this.translate.x }, point) - sidebarWidth;
        ^(ix/beatSize);
    }
    pointToY { // return the y value at the point. must be a non-translated point. - FIX
        | point | // should also accept numbers instead of just Points.
        var iy = if(point.isKindOf(Point), { point.y }, point) - topbarHeight;
        ^(ySpec.maxval-((iy-translate.y)/vSize));
    }
    beatToX { // return the (non-translated) x-position of the provided beat.
        | beat |
        ^(beat*(beatSize));
    }
    noteToY { // return the (non-translated) (top) y-position of the provided 'midinote'.
        | note |
        ^(vSize*(ySpec.maxval-note));
    }
    noteToRect {
        | note |
        ^Rect(
            this.beatToX(note[\beat]),
            this.noteToY(note.use({ \midinote.envirGet.value; })?60),
            (note.use({\sustain.envirGet.value;}))*beatSize, // FIX: use event.use{ \sustain.envirGet } or w/e instead
            vSize
        );
    }
    translate_ { // translation of the piano roll grid.
        | point |
        translate = Point(
            point.x.min(0),
            point.y.round(vSize).min(0).max(-1*((vSize*(ySpec.range+1))-(view.bounds.height-topbarHeight))),
        );
        this.refresh;
    }
    translatePoint { // translates a point on the view to a point in the piano roll (i.e. 0,0 on piano roll is actually 40,-940 on the whole view)
        | point |
        ^Point(point.x-translate.x-sidebarWidth, point.y-translate.y-topbarHeight);
    }
    untranslatePoint {
        | point |
        ^Point(point.x+translate.x+sidebarWidth, point.y+translate.y+topbarHeight);
    }
    grid_ {
        | bool |
        grid = bool;
        this.refresh;
    }
    sidebarWidth_ {
        | width |
        sidebarWidth = width;
        this.refresh;
    }
    ySpec_ {
        | spec |
        ySpec = spec.asSpec;
        this.refresh;
    }
    vSize_ {
        | size |
        vSize = size;
        this.refresh;
    }
    beatSize_ {
        | size |
        beatSize = size;
        this.refresh;
    }
    beatQuant_ {
        | quant |
        beatQuant = quant;
        // this.message("beatQuant set to" + quant.asString);
        this.refresh;
    }
    centerOn { // FIX
        | num |
        var numRows = view.bounds.height/vSize;
        this.topOn(num+(numRows/2));
    }
    topOn { // make the number the top row in the view
        | num |
        this.translate = Point(this.translate.x, -1*vSize*(ySpec.maxval-num));
    }
    isPlaying {
        ^Pdef(pdefSymbol).hasEnded.not;
    }
    play {
        | start end loop=false |
        var pat = Plazy({
            var pr = this;
            var pattern = pr.sequence.asPattern;
            var pbeats = pr.sequence.dur;
            Pchain(
                Pbind(\__, Pfunc({
                    | e |
                    pr.notePlay(e);
                })),
                Psync(pattern, pbeats, pbeats),
            );
        });
        Pdef(pdefSymbol, if(loop, Pn(pat), pat));
        Pdef(pdefSymbol).play;
    }
    stop {
        Pdef(pdefSymbol).stop;
    }
    playPause {
        | start end loop=false |
        if(this.isPlaying, {
            this.stop;
        }, {
            this.play(start, end, loop);
        });
    }
    refresh {
        view.refresh;
    }
    help {
        this.keymap.helpInfo.collect({
            | info |
            info[0] ++ " -- " ++ info[1];
        }).do({
            | msg |
            this.message(msg);
        });
    }
}

/*
+ Pattern {
pianoRoll {

}
}
*/