# Looking for Docs?

**❯❯❯ [Here you go!](https://mesosphere.github.io/dcos-commons/) ❮❮❮**

# Creating Docs for the DC/OS SDK

Docs are published automatically by CI to the `gh-pages` branch whenever changes hit `master`.
You should therefore be able to simply edit the contents of this directory and have the changes apply shortly after they're merged.
The following describe what's needed to manually generate the docs on your system for local proofreading.

## Requirements

1. `jekyll` installed via `gem install jekyll` (note: you may need to install `ruby-dev` beforehand)
2. Java SDK including `javadoc`

## View docs locally

1. Run `./generate.sh` locally from the `docs/` folder.
2. Visit the provided `file://` URL in your browser.
