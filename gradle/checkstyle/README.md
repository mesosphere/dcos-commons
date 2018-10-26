## Checkstyle Settings

[checkstyle.xml](checkstyle.xml) and [suppressions.xml](suppressions.xml) are used to validate the linting on SDK codebase. A simple `gradle check` can be used to validate the rules and see all the violations on command line. The same command also generates a html report. 

Most of the linting rules are taken from google style guide with some customization. For real-time validation (validation as you type) on the code using an Intellij has [Checkstyle-IDEA](https://plugins.jetbrains.com/plugin/1065-checkstyle-idea) plugin, `vim` has [vim-syntastic](https://github.com/vim-syntastic/syntastic/blob/master/syntax_checkers/java/checkstyle.vim), Eclipse has [Checkstyle Plugin](https://checkstyle.org/eclipse-cs/) etc..,

#### Checkstyle-IDEA

This plugin does not support all the rules that we use out of box. Following needs to be done manually if using this plugin:

- Navigate to `Settings  > Editor > Code Style > Java`
- Import the `checkstyle.xml` as a new Scheme.
- Set the `Class count to use import with '*'` to a very large value (like `99`).
- Set the `Import Layout` as
  0. `com.mesosphere.*` followed by a blank line
  0. All third party imports followed by a blank line
  0. `javax.*` followed by a blank line
  0. `java.*` imports  
