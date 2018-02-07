# Looking for Docs?

**❯❯❯ [Here you go!](https://mesosphere.github.io/dcos-commons/) ❮❮❮**

# Creating Docs for the DC/OS SDK

Docs are published automatically by CI to the `gh-pages` branch whenever changes hit `master`.
You should therefore be able to simply edit the contents of this directory and have the changes apply shortly after they're merged.
The following describe what's needed to manually generate the docs on your system for local proofreading.

## File locations

Paths are relative to the root of `dcos-commons`:

- `frameworks/*/docs/`: Service docs. `generate.sh` automatically creates symlinks to these in `docs/pages/services/`.
- `docs/pages/_includes/services`: Templates used for service docs. **Most content should go here unless it's specific to a single service.**
- `docs/pages/ops-guide/*.md`: Individual sections of the Ops Guide. These are rendered as a single page by `docs/pages/operations-guide.md`. **This may be removed in favor of the service doc templates?**
- `docs/pages/*.md`: Other guides/tutorials like the Dev guide and YAML reference. Unlike the service docs, these mainly center around service developers using the SDK itself.
- `docs/reference/swagger-api/swagger-spec.yaml`: The swagger specification used to generate the Scheduler API reference.

## Template parameters

The pages in `docs/pages/_includes/services` are templates containing content shared across multiple services. As such, these pages typically have some template parameters so that they can be portable between services. Many of these parameters are custom to the template in question, but many of them have the following parameters by convention:
- `packageName`: The name of the package, e.g. `cassandra` or `beta-kafka`.
- `serviceName`: The default name of the service when installed, e.g. `hdfs`.
- `techName`: The user-friendly name of the underlying technology, e.g. `Apache Cassandra`.

To see the parameters that a given template has, you can search the template content for `include.`. A value named `include.foo` within the template should be provided as a parameter named `foo`. For more information about template parameters, see the [Jekyll docs](https://jekyllrb.com/docs/includes/) on templating.

## Build requirements

1. `ruby-dev` for the `gem` command
2. `jekyll` and the `redirect-from`/`toc` plugins installed via `gem install`:
    ```bash
    gem install --no-document --version="3.7.0" jekyll \
    && gem install --no-document --version="0.13.0" jekyll-redirect-from \
    && gem install --no-document --version="0.5.1" jekyll-toc
    ```
3. Java SDK including `javadoc`

## Generate and view docs locally

1. Run `./generate.sh` locally from the `docs/` folder.
2. Visit the provided `http://` URL in your browser.
3. When finished, exit `generate.sh` by pressing Ctrl+C. If it's stuck, then run `killall http.py` to get it unstuck.

# Notes

The page uses the [Dropdown](http://code.stephenmorley.org/javascript/touch-friendly-drop-down-menus/) Javascript library by Stephen Morley for rendering the menus. This library is licensed [CC0 1.0 Universal](https://creativecommons.org/publicdomain/zero/1.0/legalcode).
