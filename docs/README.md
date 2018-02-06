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

## Requirements

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
