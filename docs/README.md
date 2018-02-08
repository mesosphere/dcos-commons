# Looking for Docs?

**❯❯❯ [Here you go!](https://mesosphere.github.io/dcos-commons/) ❮❮❮**

# Creating Docs for the DC/OS SDK

Docs are published automatically by CI to the `gh-pages` branch whenever changes hit `master`.
You should therefore be able to simply edit the contents of this directory and have the changes apply shortly after they're merged.
The following describe what's needed to manually generate the docs on your system for local proofreading.

## File locations

Paths are relative to the root of `dcos-commons`:

Service docs:
- `frameworks/*/docs/`: Service docs. `generate.sh` will automatically create symlinks to these from `docs/pages/services/` so that Jekyll will see them.
- `docs/pages/_includes/services/`: Service doc templates. **Most content should go here unless it's specific to a single service.**
- `docs/pages/_data/services/`: Data used for service docs and service doc templates. **Service-specific values for templates go here to simplify overriding.**

SDK docs:
- `docs/pages/ops-guide/*.md`: Individual sections of the Ops Guide. These are rendered as a single page by `docs/pages/operations-guide.md`. **This may be removed in favor of the service doc templates?**
- `docs/pages/*.md`: SDK guides/tutorials like the Dev guide and YAML reference. Unlike the service docs, these mainly center around service developers using the SDK itself.
- `docs/reference/swagger-api/swagger-spec.yaml`: The swagger specification used to generate the Scheduler API reference.

## Template parameters

All service-specific template parameters are stored in YAML files within `docs/pages/_data/services/`. This data is then used by the service docs and the service doc templates. Breaking this information into separate files has two benefits:
- Catalog all the available template parameters across services in a single place.
- Allow easy overriding when releasing documentation for a package, by editing the content of the appropriate YAML file(s). For example, `s/packageName: beta-cassandra/packageName: cassandra/` or `s/techName: Apache Kafka/techName: Confluent Kafka/`.

Some parameters are common across templates, while others are only applicable to a single template. The common parameters are:
- `packageName`: The name of the package, e.g. `cassandra` or `beta-kafka`.
- `serviceName`: The default name of the service when installed, e.g. `hdfs`.
- `techName`: The user-friendly name of the underlying technology, e.g. `Apache Cassandra` or `Elastic`.

### From service docs

To access YAML template data in e.g. `MYSERVICE.yml` from a service doc in `frameworks/*/docs/`, do the following:
  ```
  {% assign data = site.data.services.MYSERVICE %}
  Hello, {{ data.packageName }}!
  ```

**Note:** To see all existing parameters used in the service docs (and check for bad or missing parameters), do this:

```
egrep -Roh "\{\{([a-zA-Z0-9. ]+)\}\}" frameworks/*/docs/ | sort | uniq
```

### From service doc templates

To access this `MYSERVICE.yml` template data from a service doc template in `docs/pages/_includes/services/`, do the following:
- In the service doc that's using the template, point to the desired YAML file, and pass its content into the template:
  ```
  {% assign data = site.data.services.MYSERVICE %} <!-- may have this already, per above -->
  {% include services/mytemplate.md data=data %}
  ```
- In the template, use the data in the passed `data` parameter:
  ```
  Hello, common value {{ include.data.packageName }}, template-specific value {{ include.data.THISTEMPLATE.someParam }}!
  ```

**Note:** To see all existing parameters used in the templates (and check for bad or missing parameters), do this:

```
egrep -Roh "\{\{([a-zA-Z0-9. ]+)\}\}" docs/pages/_includes/services/ | sort | uniq
```

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

The page uses the [Dropdown](http://code.iamkate.com/javascript/touch-friendly-drop-down-menus/) Javascript library by Kate Morley for rendering the menus. The library is licensed [CC0 1.0 Universal](https://creativecommons.org/publicdomain/zero/1.0/legalcode).
