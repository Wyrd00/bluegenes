# Local build

Start by installing the dependencies in [System Requirements](/docs/building.md#system-requirements), then [Download NPM dependencies](/docs/building.md#download-npm-dependencies) and finally run the command in [Running a dev environment](/docs/building.md#running-a-dev-environment). For more information, refer to the rest of this document or the [FAQ](/docs/useful-faqs.md).

## System Requirements

* OpenJDK, version 8 (only until we make our software compatible with OpenJDK 11)
* Latest [Leiningen](https://leiningen.org/)
* Latest supported [nodejs](https://nodejs.org/).  You can check your version using `node -v`). We recommend installing node using [nvm](https://github.com/creationix/nvm)
* Latest supported [npm](https://www.npmjs.com/)

## Download NPM dependencies

    npm install

## Quickstart

These commands are explained in more depth below, but if you know what you want here's a quick reference of the most useful ones.

    lein dev         # start dev server with hot-reloading
    lein repl        # start dev server with hot-reloading and nrepl (no clean or css)
    lein prod        # start prod server
    lein deploy      # build prod release and deploy to clojars

    lein format      # run cljfmt to fix code indentation
    lein kaocha      # run unit tests
    npx cypress run  # run cypress ui tests

## Running a dev environment

You can start a complete developer environment with automatic compilation of Less CSS and hot-reloading of code changes by running:

    lein dev

However, all the output will be thrown into one terminal. If you wish to keep the processes separate, you can start them each individually by following the instructions below.

### Compile the CSS

We use [less](http://lesscss.org/) to write our styles.

If you only want the CSS to be uploaded once, run:

    lein less once

Note: in this case you will have to manually recompile the CSS files after each change.

If you will be changing the CSS files continuously, you can automatically recompile the CSS files after each change using:

    lein less auto

Note: even that you will not see a prompt telling you when it's complete, the browser page will automatically refresh.


### Make Leiningen reload code changes in the browser


    lein figwheel dev

Note: if you use `lein run` or any alias calling it like `dev` or `repl`, Figwheel will be started automatically.

### Start the web server

If you have OpenJDK 8:

    lein with-profile +dev run

If you have OpenJDK 9:

    lein with-profile +java9 figwheel


By default, the web server will be started on http://localhost:5000/. To change this value, edit the corresponding `config.edn`.


## Running tests

### Unit tests

You can run the ClojureScript unit tests by invoking the test runner kaocha.

    lein kaocha

Kaocha also has a watch mode that's useful when editing the tests.

    lein kaocha --watch

If you need something faster, evaluating `(cljs.test/run-tests)` from a connected editor would be even better. Although with async tests, you'll need some way of [having your editor report the results](https://clojurescript.org/tools/testing#detecting-test-completion-success).

### Cypress integration tests

Make sure BlueGenes is running by using `lein dev` or `lein prod`. (Preferably make sure they pass in *prod*, but *dev* can be useful for stack traces.)

Run all the Cypress tests:

    npx cypress run

Cypress also has a really useful interface for debugging failing tests:

    DEBUG=cypress:* npx cypress open

# Production Builds

## Testing a minified instance before deploying:

Most of the time, we develop with uncompressed files - it's faster for hot reloads. But for production, we want things to be extra fast on load and we don't hot-reload changes, so it's better to present minified files that have had all un-necessary code stripped. Clojure uses Google Closure tools (yes, Clojure uses Closure) to minify things.

Sometimes the Closure compiler is overzealous and removes something we actually wanted to keep. To check what your work looks like in a minified build, run this in the terminal (I'd recommend closing any existing lein run / lein figwheel sessions first).

    lein with-profile prod cljsbuild once min
    lein with-profile prod run

There is also a shortcut that in addition cleans and compiles CSS.

    lein prod


## Deploying your build

One of the easiest ways to deploy the prod minified version is to set up [Dokku](http://dokku.viewdocs.io/dokku/) on your intended server. You can also use BlueGenes with [heroku](https://www.heroku.com/).


### Minified deployment using dokku

Once dokku is configured on your remote host, you'll need to add your public key, create a remote for your host and push to it:

    # On your dokku host
    sudo dokku ssh-keys:add your-user /path/to/your/public/key
    # On your dev computer
    git remote add dokku dokku@your-host:bluegenes
    git push dokku master

If you want to deploy a different branch, you can use `git push dokku dev:master` (replace *dev* with the branch you wish to deploy from).

### Uberjar

It's also possible to compile BlueGenes to a jar that will automatically launch a server when executed.

To compile and package BlueGenes into an executable jar, run the following command in the project folder:

    lein uberjar

Then, to start the application, execute the jar and pass in a [`config.edn` file](../config/dev/README.md):

    java -jar -Dconfig="config/prod/config.edn" target/bluegenes.jar

(For security reasons, the `config.edn` file used to execute the jar can be located anywhere, including your home directory.)


### Launching your uberjar with InterMine

InterMine 2.0 includes a [Gradle target to launch a BlueGenes instance](https://intermine.readthedocs.io/en/latest/system-requirements/software/gradle/index.html#deploy-blue-genes).

By default, it launches the latest BlueGenes release from Clojars. If you want to update the version of the JAR being launched, you'll need to create an uberjar (see above).


### Deploying your uberjar to Clojars

Official BlueGenes releases can be deployed to [Clojars](https://clojars.org/), under the [org.intermine Clojars organisation](https://clojars.org/groups/org.intermine).

When deploying BlueGenes to Clojars, the JAR file should include all compiled assets: this includes JavaScript, less, and vendor libraries. This allows other projects to include BlueGenes as a dependency and deploy the client and server without needing to compile BlueGenes.

To deploy a compiled JAR to Clojars, simply use the `deploy` alias which automatically includes the `uberjar` profile and targets Clojars.

    $ lein deploy

### Releasing a new version

The release process is a combination of the above commands, with some additional steps. Generally, you'll want to do the following.

1. Update the version number in **project.clj**.
1. Commit this change and tag it using `git tag -a v1.0.0 -m "Release v1.0.0"`, replacing *1.0.0* with your version number.
1. Push your commit and tag using `git push origin` followed by `git push origin v1.0.0` (again replace *1.0.0* with your version number). Make sure that you push to the intermine repository, not just your fork!
1. Deploy a new uberjar to Clojars with `lein deploy`.
1. Deploy the latest release to dokku with `git push dokku dev:master`.

# Troubleshooting

1. When things get weird, you might consider clearing both your browser's and BlueGene's cache data. To clear BlueGenes', click on the cog in the top right, then "developer". There should be a big blue button that clears local storage.
2. Verify what branch you have checked out. `dev` is our main development branch, whereas `master` is our production branch.
3. Verify that the InterMine web services ("InterMines") you are using are running the latest InterMine release. You will find a list of InterMines and their current version under the key `intermine_version` in the [InterMine registry](http://registry.intermine.org/service/instances). The changelog for InterMine release versions is [available on GitHub](https://github.com/intermine/intermine/releases).
4. Remember that you can always change which InterMine you're using in BlueGenes by using the cog (top right).

If none of these tips help you, create an [issue](https://github.com/intermine/bluegenes/issues) or [contact us (via chat, email, mailing list, etc.)](http://intermine.readthedocs.io/en/latest/about/contact-us/)
