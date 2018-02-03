<a name="package-versioning-scheme"></a>
## Package Versioning Scheme

- {{ include.tech_name }}: latest stable at the time of the release, refer to the package version.
- DC/OS: 1.10 or higher.

Packages are versioned with an `a.b.c-x.y.z` format, where `a.b.c` is the version of the DC/OS integration and `x.y.z` indicates the version of {{ include.tech_name }}. For example, `2.1.1-{{ include.tech_version }}` indicates version `2.1.1` of the DC/OS integration and version `{{ include.tech_version }}` of {{ include.tech_name }}.

<a name="version-policy"></a>
## Version Policy
The DC/OS {{ include.tech_name }} Service is engineered and tested to work with a specific release of {{ include.tech_policy_desc }}. We select stable versions of the base technology in order to promote customer success. We have selected the latest stable version of {{ include.tech_name }} for new releases.

<a name="contacting-technical-support"></a>
## Contacting Technical Support

### Mesosphere DC/OS
[Submit a request](https://support.mesosphere.com/hc/en-us/requests/new).
