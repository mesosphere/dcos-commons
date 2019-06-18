# Looking for Docs?

**❯❯❯ [Here you go!](https://mesosphere.github.io/dcos-commons/) ❮❮❮**

# Creating Docs for the DC/OS SDK

This directory contains developer-facing documentation of the SDK itself. User-facing documentation for the Cassandra, Elastic, HDFS, and Kafka services can be found at the [Main DC/OS docs site](https://docs.mesosphere.com/services/).

Docs are published automatically whenever a commit is pushed to `master`. The rendered HTML output is placed in a separate `gh-pages` branch by CI.
You should therefore be able to simply edit the contents of this directory and have the changes apply shortly after they're merged into `master`.

## File locations

Paths are relative to the root of `dcos-commons`:

- `generate.sh`: Script which renders the site and runs a temporary HTTP server to view it.
- `docs/pages/*.md`: SDK guides/tutorials like the Dev guide and YAML reference.
- `docs/reference/swagger-api/swagger-spec.yaml`: The swagger specification used to generate the Scheduler API reference.

This site used to also host per-service user documentation, but that was moved into the [Main DC/OS docs site](https://docs.mesosphere.com/services/). The prior Operations Guide has also been folded into the per-Service documentation.

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
3. When finished, exit `generate.sh` by pressing Ctrl+C.

# Notes

The page uses the [Dropdown](https://code.iamkate.com/javascript/touch-friendly-drop-down-menus/) Javascript library by Kate Morley for rendering the menus. The library is licensed [CC0 1.0 Universal](https://creativecommons.org/publicdomain/zero/1.0/legalcode).
