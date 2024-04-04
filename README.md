# cljrun

A lightweight task runner for Clojure.

Similar to [Babashka Tasks](https://book.babashka.org/#tasks), but doesn't require
anything to be installed besides `clj`. I made it because, based on feedback from some
[Biff](https://biffweb.com) users, it turns out not everyone already has Babashka
installed or even knows what it is. Given Biff's focus on keeping things streamlined, I
thought eliminating the extra dependency would be worthwhile. The extra startup time for
clj-based tasks has turned out to not matter in my experience (more on that below).

There isn't much code in this library&mdash;only about 60 lines. It's more about the
*approach*. cljrun makes it easy to provide collections of tasks that can be shared
between projects. Biff provides a collection of Biff-specific tasks, for example. It would
be interesting to make a "default"/less opinionated collection of tasks, e.g. for creating
new (non-Biff) projects, generating uberjars, etc. I think this could help give tools.deps
the same kind of out-of-the-box productivity that currently tends to be associated more
with Leiningen.

It seems like with the right incantation you should be able to use cljrun via Babashka,
giving the best of both worlds: no hard dependencies beyond clj, but the option of
Babashka's quick startup time for those who want it.

If you want to chat about cljrun, `#biff` on [Clojurians Slack](http://clojurians.net) is
a good place.

## Usage

You need to define a map from task names to function vars. Make a `dev/tasks.clj` file
with something like this in it:

```clojure
(ns tasks)

(defn hello
  "Prints a friendly greeting."
  []
  (println "hello"))

(def tasks
  {"hello" #'hello})
```

Then stick this in your `deps.edn` file:

```clojure
:aliases
{:run {:extra-deps {com.biffweb/cljrun {:git/url "https://github.com/jacobobryant/cljrun"
                                        :git/tag "v1.0.0"
                                        :git/sha "4f9bb38"}}
       :extra-paths ["dev"]
       :main-opts ["-m" "com.biffweb.cljrun" "tasks/tasks"]}}
```

Now you can invoke the `hello` task with `clj -M:run hello`. Run `clj -M:run --help` to
get a list of all the available tasks with the first line of their doc strings, and run
`clj -M:run <task> --help` to see the full doc string for a particular task:

```bash
$ clj -M:run --help
Available commands:

  goodbye - Prints a friendly farewell
  hello   - Prints a friendly greeting.
$ clj -M:run hello --help
Prints a friendly greeting.

To be specific, the greeting is 'hello.'
```

I like to have `alias cljrun=clj -M:run` in my `.bashrc`, so you can type e.g. `cljrun
hello`.

### Keeping startup time low

If some of your tasks have their own dependencies, you can separate each task into its own
namespace and load them on-demand with `requiring-resolve`:

```clojure
(defn hello
  "Prints a friendly greeting."
  [& args]
  (apply (requiring-resolve 'tasks.hello/hello) args))

(defn goodbye
  "Prints a friendly farewell."
  [& args]
  (apply (requiring-resolve 'tasks.goodbye/goodbye) args))

(def tasks
  {"hello" #'hello
   "goodbye" #'goodbye})
```

You can go further and use `requiring-resolve` any time you call a function from an
external library&mdash;I experimented with this, aided by macros to make it a bit more
ergonomic&mdash;but in most cases the approach shown above is probably good enough.

### Sharing tasks between projects

There are two options here. First, you can edit your `deps.edn` to point to a tasks map
defined in some library:

```clojure
:aliases {:run {:extra-deps {com.example/tasks {:mvn/version "1.0.0"}}
                :main-opts ["-m" "com.biffweb.cljrun" "com.example/tasks"]}}
```

(Assuming `com.example/tasks` depends on `com.biffweb/cljrun`, you don't have to depend on
the latter explicitly.)

If you still want to define some project-specific tasks, you can instead merge the other
tasks into your tasks map:

```clojure
;; deps.edn
:aliases {:run {:extra-deps {com.example/tasks {:mvn/version "1.0.0"}}
                :extra-paths ["dev"]
                :main-opts ["-m" "com.biffweb.cljrun" "tasks/tasks"]}}


;; dev/tasks.clj
(ns tasks
  (:require [com.example :as example]))

(defn hello
  "Prints a friendly greeting."
  []
  (println "hello"))

(def tasks
  (merge example/tasks
         {"hello" #'hello}))
```

### Tasks that call each other

The simplest thing to do is just have the tasks call each other directly:

```clojure
(defn css
  "Generates CSS."
  [& args]
  ...)

(defn deploy
  "Builds and deploys the app."
  []
  (css "--minify")
  ...)

(def tasks
  {"css" #'css
   "deploy" #'deploy})
```

If that isn't quite enough indirection for you, cljrun provides a `run-task` function that
does almost the same thing:

```clojure
(ns tasks
  (:require [com.biffweb.cljrun :refer [run-task]]))

...

(defn deploy
  "Builds and deploys the app."
  []
  (run-task "css" "--minify")
  ...)
```

The difference is that `run-task` will invoke whichever task was defined in the tasks map.
This is useful if you want to provide a set of tasks to be used by [multiple
projects](#sharing-tasks-between-projects) while allowing each project to override
individual tasks. In our example above, a project could define its own `css` task but
stick with the default `deploy` task:

```clojure
(ns tasks
  (:require [com.example :as example]))

(defn css
  "Generates CSS."
  [& args]
  ...)

(def tasks
  (merge example/tasks ; includes a `deploy` task which will invoke our custom
                       ; `css` task
         {"css" #'css}))
```

### Command line options and configuration

cljrun doesn't do any CLI parsing&mdash;do it however you want. For simple flags you could
do something like this:

```clojure
(defn hello
  "Prints a friendly greeting.

   Options:

   --loud
       Prints the greeting in all caps."
  [& args]
  (let [loud (some #{"--loud"} args)]
    (if loud
      (println "HELLO")
      (println "hello"))))
```

Same goes for configuration. Have your task slurp up a `config.edn` file, read stuff from
the environment, whatever.

## More thoughts about making tools.deps easy

`deps.edn` aliases are nice when you really do need different classpaths, e.g. for
separating dev dependencies from prod, or for testing a library against different versions
of Clojure. But as a mechanism for defining project tasks they're a bit clunky. In
particular, if you define a collection of tasks as a bunch of aliases&mdash;one alias per
task&mdash;then it's not very ergonomic to share that collection with others. Either they have
to do a bunch of copy-and-pasting or use some fancy tooling to update their `deps.edn`. If
we define our task *collections* in libraries&mdash;not just the individual task
implementations&mdash;then we can reuse all the infrastructure and tooling we already have for
sharing library code.

I probably should go ahead and define a default set of tasks right in the cljrun repo. You
could even put a `:run` alias in your `~/.clojure/deps.edn` file, which would take effect
if your local project doesn't have its own `:run` alias.
