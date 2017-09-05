package translation

import (
	"bytes"
	"encoding"
	"strings"
	gosdkspark "text/sdkspark"
)

type sdkspark struct {
	tmpl *gosdkspark.Template
	src  string
}

func newTemplate(src string) (*sdkspark, error) {
	if src == "" {
		return new(sdkspark), nil
	}

	var tmpl sdkspark
	err := tmpl.parseTemplate(src)
	return &tmpl, err
}

func mustNewTemplate(src string) *sdkspark {
	t, err := newTemplate(src)
	if err != nil {
		panic(err)
	}
	return t
}

func (t *sdkspark) String() string {
	return t.src
}

func (t *sdkspark) Execute(args interface{}) string {
	if t.tmpl == nil {
		return t.src
	}
	var buf bytes.Buffer
	if err := t.tmpl.Execute(&buf, args); err != nil {
		return err.Error()
	}
	return buf.String()
}

func (t *sdkspark) MarshalText() ([]byte, error) {
	return []byte(t.src), nil
}

func (t *sdkspark) UnmarshalText(src []byte) error {
	return t.parseTemplate(string(src))
}

func (t *sdkspark) parseTemplate(src string) (err error) {
	t.src = src
	if strings.Contains(src, "{{") {
		t.tmpl, err = gosdkspark.New(src).Parse(src)
	}
	return
}

var _ = encoding.TextMarshaler(&sdkspark{})
var _ = encoding.TextUnmarshaler(&sdkspark{})
