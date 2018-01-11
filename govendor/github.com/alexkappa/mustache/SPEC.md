# Spec Conformance

The following table describes the conformance to the [mustache/spec](https://github.com/mustache/spec).

| Spec              | Test                                         | Status |
| ---               | ---                                          | ---    |
| Comments          | Inline                                       | Pass   |
| Comments          | Multiline                                    | Pass   |
| Comments          | Standalone                                   | Pass   |
| Comments          | Indented Standalone                          | Pass   |
| Comments          | Standalone Line Endings                      | Pass   |
| Comments          | Standalone Without Previous Line             | Pass   |
| Comments          | Standalone Without Newline                   | Pass   |
| Comments          | Multiline Standalone                         | Pass   |
| Comments          | Indented Multiline Standalone                | Pass   |
| Comments          | Indented Inline                              | Pass   |
| Comments          | Surrounding Whitespace                       | Pass   |
| Delimiters        | Pair Behavior                                | Pass   |
| Delimiters        | Special Characters                           | Pass   |
| Delimiters        | Sections                                     | Pass   |
| Delimiters        | Inverted Sections                            | Pass   |
| Delimiters        | Partial Inheritence                          | Pass   |
| Delimiters        | Post-Partial Behavior                        | Pass   |
| Delimiters        | Surrounding Whitespace                       | Pass   |
| Delimiters        | Outlying Whitespace (Inline)                 | Pass   |
| Delimiters        | Standalone Tag                               | Pass   |
| Delimiters        | Indented Standalone Tag                      | Pass   |
| Delimiters        | Standalone Line Endings                      | Pass   |
| Delimiters        | Standalone Without Previous Line             | Pass   |
| Delimiters        | Standalone Without Newline                   | Pass   |
| Delimiters        | Pair with Padding                            | Pass   |
| Interpolation     | No Interpolation                             | Pass   |
| Interpolation     | Basic Interpolation                          | Pass   |
| Interpolation     | HTML Escaping                                | Pass   |
| Interpolation     | Triple Mustache                              | Pass   |
| Interpolation     | Ampersand                                    | Pass   |
| Interpolation     | Basic Integer Interpolation                  | Pass   |
| Interpolation     | Triple Mustache Integer Interpolation        | Pass   |
| Interpolation     | Ampersand Integer Interpolation              | Pass   |
| Interpolation     | Basic Decimal Interpolation                  | Pass   |
| Interpolation     | Triple Mustache Decimal Interpolation        | Pass   |
| Interpolation     | Ampersand Decimal Interpolation              | Pass   |
| Interpolation     | Basic Context Miss Interpolation             | Pass   |
| Interpolation     | Triple Mustache Context Miss Interpolation   | Pass   |
| Interpolation     | Ampersand Context Miss Interpolation         | Pass   |
| Interpolation     | Dotted Names - Basic Interpolation           | Pass   |
| Interpolation     | Dotted Names - Triple Mustache Interpolation | Pass   |
| Interpolation     | Dotted Names - Ampersand Interpolation       | Pass   |
| Interpolation     | Dotted Names - Arbitrary Depth               | Pass   |
| Interpolation     | Dotted Names - Broken Chains                 | Pass   |
| Interpolation     | Dotted Names - Broken Chain Resolution       | Pass   |
| Interpolation     | Dotted Names - Initial Resolution            | Pass   |
| Interpolation     | Dotted Names - Context Precedence            | Pass   |
| Interpolation     | Interpolation - Surrounding Whitespace       | Pass   |
| Interpolation     | Triple Mustache - Surrounding Whitespace     | Pass   |
| Interpolation     | Ampersand - Surrounding Whitespace           | Pass   |
| Interpolation     | Interpolation - Standalone                   | Pass   |
| Interpolation     | Triple Mustache - Standalone                 | Pass   |
| Interpolation     | Ampersand - Standalone                       | Pass   |
| Interpolation     | Interpolation With Padding                   | Pass   |
| Interpolation     | Triple Mustache With Padding                 | Pass   |
| Interpolation     | Ampersand With Padding                       | Pass   |
| Inverted Sections | Falsey                                       | Pass   |
| Inverted Sections | Truthy                                       | Pass   |
| Inverted Sections | Context                                      | Pass   |
| Inverted Sections | List                                         | Pass   |
| Inverted Sections | Empty List                                   | Pass   |
| Inverted Sections | Doubled                                      | Pass   |
| Inverted Sections | Nested (Falsey)                              | Pass   |
| Inverted Sections | Nested (Truthy)                              | Pass   |
| Inverted Sections | Context Misses                               | Pass   |
| Inverted Sections | Dotted Names - Truthy                        | Pass   |
| Inverted Sections | Dotted Names - Falsey                        | Pass   |
| Inverted Sections | Dotted Names - Broken Chains                 | Pass   |
| Inverted Sections | Surrounding Whitespace                       | Pass   |
| Inverted Sections | Internal Whitespace                          | Pass   |
| Inverted Sections | Indented Inline Sections                     | Pass   |
| Inverted Sections | Standalone Lines                             | Pass   |
| Inverted Sections | Standalone Indented Lines                    | Pass   |
| Inverted Sections | Standalone Line Endings                      | Pass   |
| Inverted Sections | Standalone Without Previous Line             | Pass   |
| Inverted Sections | Standalone Without Newline                   | Pass   |
| Inverted Sections | Padding                                      | Pass   |
| Partials          | Basic Behavior                               | Pass   |
| Partials          | Failed Lookup                                | Pass   |
| Partials          | Context                                      | Pass   |
| Partials          | Recursion                                    | Pass   |
| Partials          | Surrounding Whitespace                       | Pass   |
| Partials          | Inline Indentation                           | Pass   |
| Partials          | Standalone Line Endings                      | Fail   |
| Partials          | Standalone Without Previous Line             | Fail   |
| Partials          | Standalone Without Newline                   | Fail   |
| Partials          | Standalone Indentation                       | Fail   |
| Partials          | Padding Whitespace                           | Pass   |
