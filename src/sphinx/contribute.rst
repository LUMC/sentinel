Contributing
============

Any type of contributions is very welcomed and appreciated :)! From bug reports to new features, there is always room
to help out.

Quick Links
-----------

* Issue tracker: `https://git.lumc.nl/sasc/sentinel/issues <https://git.lumc.nl/sasc/sentinel/issues>`_

* Source code: `https://git.lumc.nl/sasc/sentinel <https://git.lumc.nl/sasc/sentinel>`_

* Git: `git@git.lumc.nl:sasc/sentinel.git <git@git.lumc.nl:sasc/sentinel.git>`_

Bug Reports & Feature Suggestions
---------------------------------

Feel free to report bugs and/or suggest new features about our local LUMC deployment or Sentinel in general to our
`issue tracker <https://git.lumc.nl/sasc/sentinel/issues>`_. We do request that you be as descriptive as possible.
Particularly for bugs, please describe in as much detail as possible what you expected to see and what you saw instead.

Documentation
-------------

Documentation updates and/or fixes are very appreciated! We welcome everything from one-letter typo fixes to new 
documentation sections, be it in the internal ScalaDoc or our user guide (the one you're reading now). You are free to
submit a pull request for documentation fixes. If you don't feel like cloning the entire code, we are also happy if you
open an issue on our issue tracker.

Bug Fixes
---------

Bug fix contributions requires that you have a local development environment up and running. Head over to the
:doc:`devs_setup` section for a short guide on how to do so.

To find bugs to fix, you can start by browsing our issue tracker for issues labeled with ``bug``. You can also search
through the source code for ``FIXME`` notes. Having found an issue you would like to fix, the next steps would be:

1. Create a new local branch, based on the last version of `master`.
2. Implement the fix.
3. Make sure all test passes. If the bug has not been covered by any of our tests, we request that new tests be added
   to protect against regressions in the future.
4. Commit your changes.
5. Submit a pull request.

We will then review your changes. If it is all good, it will be rebased to ``master`` and we will list your name in our
contributors list :).

And yes, we did say rebase up there, not merge. We prefer to keep our git history linear, which means changes will be
integrated to ``master`` via ``git rebase`` and not ``git merge``.

New Features
------------

Feature implementations follow almost the same procedure as `Bug Fixes`_. The difference being that you are not limited
to the feature requests we list on the issue tracker. If you have a new idea for a new feature that has not been listed
anywhere, you are free to go ahead and implement it. We only ask that if you do wish to have the feature merged with
the `master` branch that you communicate with us first, mainly to prevent possible duplicate works.
