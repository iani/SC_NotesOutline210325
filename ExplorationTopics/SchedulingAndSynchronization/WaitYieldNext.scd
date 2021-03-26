/* 26 Mar 2021 16:01
Examples of next, yield and next in a Routine.

*/

r = {
	"starting. Playing an initial tone".postln;
	(degree: 7, dur: 0.1).play;
	"waiting for 1 second".postln;
	1.wait;
	"playing a note".postln;
	().play;
	"waiting for 2 more seconds".postln;
	2.wait;
	"playing a second note".postln;
	(degree: 1).play;
	"calling yield to stop".postln;
	"please send 'next' to this routine to continue".postln;
	nil.yield; // note: any object can answer to 'yield'. The effect is the same.
	"Continuing. Play one more sound".postln;
	(degree: 2).play;
	"Stopped with yield, again.  Send 'continue' to this routine to continue".postln;
	1.yield; // any object will do.
	"FINISHING: PLAYINGA A LONG NOTE".postln;
	(dur: 5, degree: 15).play;
	"DONE".postln;
}.fork
//:
r.next;
//:
r.continue;