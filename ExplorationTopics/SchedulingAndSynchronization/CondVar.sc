/* 26 Mar 2021 16:12
Copy of CondVar code proposed by Brian Heim with pull request 

Relevant repo link and commit SHA:
https://github.com/supercollider/supercollider/blob/73afe7243431391c1cab0a7c33ca51d2c62f59e9/SCClassLibrary/Common/Core/CondVar.sc

I want to try it out and then perhaps provide some extra explanations.

*/

CondVar {
	var waitingThreads;

	*new { ^super.newCopyArgs(Array.new, Array.new) }

	wait { |predicate|
		if(predicate.isNil) {
			this.prWait
		} {
			// If the predicate is already true, we return immediately. If not,
			// we block until the predicate is true after being signalled.
			while { predicate.value.not } { this.prWait }
		}
	}

	waitFor { |timeoutBeats, predicate|
		var endingBeats;

		// Convert timeoutBeats to a value we know is safe to call `wait` on,
		// since an exception thrown in the timeout thread is a very bad thing.
		// May throw if preconditions are not met.
		timeoutBeats = this.prHandleTimeoutPreconditions(timeoutBeats);

		// This is a slightly more complex version of the body of `wait`. The
		// extra complexity comes from maintaining the timeout requirement.
		if(predicate.isNil) {
			// Interface requirement: immediately return if timeout is non-positive
			if(timeoutBeats <= 0) { ^false };
			^this.prWaitFor(timeoutBeats)
		} {
			// By taking thisThread.beats here, we wait a little longer than we
			// should because of the time it takes to get from the start of `waitFor`
			// to this line, but that's alright since precise timeouts aren't an
			// interface requirement and it would be complicated to implement correctly.
			// If `waitUntil` is added to the interface, we could rewrite `waitFor` in
			// terms of `waitUntil` and probably have slightly better precision.
			endingBeats = thisThread.beats + timeoutBeats;

			// If the predicate is already true, we return true immediately.
			while {
				predicate.value.not
			} {
				timeoutBeats = endingBeats - thisThread.beats;
				// We only get here when the predicate is not true, so we can return
				// false immediately if we are timed out.
				if(timeoutBeats <= 0) { ^false };
				// If the timeout expires, return the value of the predicate; otherwise,
				// we were signalled, so go back through the loop.
				if(this.prWaitFor(timeoutBeats).not) { ^predicate.value };
			};

			^true
		}
	}

	signalOne {
		if (waitingThreads.isEmpty.not) {
			this.prWakeThread(waitingThreads.removeAt(0));
		}
	}

	signalAll {
		waitingThreads.do(this.prWakeThread(_));
		waitingThreads = Array.new;
	}

	shallowCopy { this.shouldNotImplement(thisMethod) }
	deepCopy { this.shouldNotImplement(thisMethod) }

	prWait {
		this.prSleepThread(thisThread);
		\wait.yield
	}

	// Returns true iff we were woken via signal (and not timeout)
	prWaitFor { |timeoutBeats|
		// precondition: timeoutBeats is a Float or Integer, positive, and not inf
		var waitingThread = thisThread;
		var didNotTimeout = true;
		var timeoutThread = Routine {
			var wokenThread;

			// We will likely wait a bit longer than timeoutBeats because of the time it
			// takes to schedule and run the new thread. We could have more precise timing
			// if we also implemented waitUntil and rewrote waitFor in terms of it.
			timeoutBeats.wait;

			didNotTimeout = false;

			// wokenThread must be non-nil. If the thread woke before this timeout fired,
			// this routine will already be stopped and we won't get here.
			wokenThread = this.prRemoveWaitingThread(waitingThread);
			this.prWakeThread(wokenThread);
		};

		timeoutThread.play(thisThread.clock);
		this.prWait;
		timeoutThread.stop;

		^didNotTimeout
	}

	prSleepThread { |t| waitingThreads = waitingThreads.add(t.threadPlayer) }
	prWakeThread { |t| t.clock.sched(0, t) }
	prRemoveWaitingThread { |t| ^waitingThreads.remove(t.threadPlayer) }

	// Precondition checks for `waitFor` timeout. May throw.
	prHandleTimeoutPreconditions { |n|
		n = case
			{ n.class === Float or: { n.class === Integer } } { n }
			{ n.respondsTo(\asInteger) } { n.asInteger }
			{ n.respondsTo(\asFloat) } { n.asFloat }
			{ n };

		if(n.class !== Float and: { n.class !== Integer }) {
			Error("Timeout must be a Float or Integer, or convertible to one").throw
		};

		if(n.isNaN) { Error("Timeout must not be NaN").throw };

		// -inf is checked in negative-time check after this.
		if(n === inf) { Error("Timeout must not be inf; use `wait` instead").throw };

		^n
	}
}
