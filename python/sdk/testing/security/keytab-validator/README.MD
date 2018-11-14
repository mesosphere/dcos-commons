This is a simple keytab validator.

The files known_good.keytab and known_bad.keytab can be used to test the validator if you need to make any changes.

A copy of the validator is checked in at keytab-validator.jar in this directory. If you make changes, update this checked
in file :).

To build the validator:
```
# From /keytab-validator:
javac -d . src/Main.java && jar -cvfe keytab-validator.jar Main Main.class
```
