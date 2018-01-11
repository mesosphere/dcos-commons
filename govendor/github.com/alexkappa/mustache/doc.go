// Copyright (c) 2014 Alex Kalyvitis

/*
Package mustache is an implementation of the mustache templating language in Go.
For more information on mustache check out the official documentation at
http://mustache.github.io.

    temlpate := mustache.New()
    err := template.ParseString("Hello, {{subject}}!")
    if err != nil {
        // handle error
    }
    s, err := template.RenderString(map[string]string{"subject": "world"})
    if err != nil {
        // handle error
    }
    fmt.Println(s)

There are several wrappers of Parse and Render to help with different input or
output types. It is quite common to need to write the output of the template to
an http.ResponseWriter. In this case the Render function is the most apropriate.

    import "net/http"
    import "github.com/alexkappa/mustache"

    func ServeHTTP(w http.ResponseWriter, r *http.Request) {
        template, err := mustache.ParseString("Hello, {{subject}}!")
        if err != nil {
            // handle error
        }
        err = template.Render(w, map[string]string{"subject": "world"})
        if err != nil {
            // handle error
        }
    }
*/
package mustache
