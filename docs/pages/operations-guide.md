---
title: SDK Operations Guide
menuWeight: 0
toc: true
---

<!-- this file just includes the per-section ops-guide files in one single page -->

{% assign sorted_pages = site.pages | sort:"menuWeight" %}
{% for p in sorted_pages %}{% assign urltokens = p.url | split: "/" %}
{% if urltokens.size == 3 and urltokens[1] == "ops-guide" %}
# {{ p.navigationTitle }}
{{ p.content | markdownify }}
{% endif %}{% endfor %}
