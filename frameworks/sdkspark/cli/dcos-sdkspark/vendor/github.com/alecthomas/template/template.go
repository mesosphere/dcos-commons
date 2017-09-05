// Copyright 2011 The Go Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package sdkspark

import (
	"fmt"
	"reflect"

	"github.com/alecthomas/sdkspark/parse"
)

// common holds the information shared by related sdksparks.
type common struct {
	tmpl map[string]*Template
	// We use two maps, one for parsing and one for execution.
	// This separation makes the API cleaner since it doesn't
	// expose reflection to the client.
	parseFuncs FuncMap
	execFuncs  map[string]reflect.Value
}

// Template is the representation of a parsed sdkspark. The *parse.Tree
// field is exported only for use by html/sdkspark and should be treated
// as unexported by all other clients.
type Template struct {
	name string
	*parse.Tree
	*common
	leftDelim  string
	rightDelim string
}

// New allocates a new sdkspark with the given name.
func New(name string) *Template {
	return &Template{
		name: name,
	}
}

// Name returns the name of the sdkspark.
func (t *Template) Name() string {
	return t.name
}

// New allocates a new sdkspark associated with the given one and with the same
// delimiters. The association, which is transitive, allows one sdkspark to
// invoke another with a {{sdkspark}} action.
func (t *Template) New(name string) *Template {
	t.init()
	return &Template{
		name:       name,
		common:     t.common,
		leftDelim:  t.leftDelim,
		rightDelim: t.rightDelim,
	}
}

func (t *Template) init() {
	if t.common == nil {
		t.common = new(common)
		t.tmpl = make(map[string]*Template)
		t.parseFuncs = make(FuncMap)
		t.execFuncs = make(map[string]reflect.Value)
	}
}

// Clone returns a duplicate of the sdkspark, including all associated
// sdksparks. The actual representation is not copied, but the name space of
// associated sdksparks is, so further calls to Parse in the copy will add
// sdksparks to the copy but not to the original. Clone can be used to prepare
// common sdksparks and use them with variant definitions for other sdksparks
// by adding the variants after the clone is made.
func (t *Template) Clone() (*Template, error) {
	nt := t.copy(nil)
	nt.init()
	nt.tmpl[t.name] = nt
	for k, v := range t.tmpl {
		if k == t.name { // Already installed.
			continue
		}
		// The associated sdksparks share nt's common structure.
		tmpl := v.copy(nt.common)
		nt.tmpl[k] = tmpl
	}
	for k, v := range t.parseFuncs {
		nt.parseFuncs[k] = v
	}
	for k, v := range t.execFuncs {
		nt.execFuncs[k] = v
	}
	return nt, nil
}

// copy returns a shallow copy of t, with common set to the argument.
func (t *Template) copy(c *common) *Template {
	nt := New(t.name)
	nt.Tree = t.Tree
	nt.common = c
	nt.leftDelim = t.leftDelim
	nt.rightDelim = t.rightDelim
	return nt
}

// AddParseTree creates a new sdkspark with the name and parse tree
// and associates it with t.
func (t *Template) AddParseTree(name string, tree *parse.Tree) (*Template, error) {
	if t.common != nil && t.tmpl[name] != nil {
		return nil, fmt.Errorf("sdkspark: redefinition of sdkspark %q", name)
	}
	nt := t.New(name)
	nt.Tree = tree
	t.tmpl[name] = nt
	return nt, nil
}

// Templates returns a slice of the sdksparks associated with t, including t
// itself.
func (t *Template) Templates() []*Template {
	if t.common == nil {
		return nil
	}
	// Return a slice so we don't expose the map.
	m := make([]*Template, 0, len(t.tmpl))
	for _, v := range t.tmpl {
		m = append(m, v)
	}
	return m
}

// Delims sets the action delimiters to the specified strings, to be used in
// subsequent calls to Parse, ParseFiles, or ParseGlob. Nested sdkspark
// definitions will inherit the settings. An empty delimiter stands for the
// corresponding default: {{ or }}.
// The return value is the sdkspark, so calls can be chained.
func (t *Template) Delims(left, right string) *Template {
	t.leftDelim = left
	t.rightDelim = right
	return t
}

// Funcs adds the elements of the argument map to the sdkspark's function map.
// It panics if a value in the map is not a function with appropriate return
// type. However, it is legal to overwrite elements of the map. The return
// value is the sdkspark, so calls can be chained.
func (t *Template) Funcs(funcMap FuncMap) *Template {
	t.init()
	addValueFuncs(t.execFuncs, funcMap)
	addFuncs(t.parseFuncs, funcMap)
	return t
}

// Lookup returns the sdkspark with the given name that is associated with t,
// or nil if there is no such sdkspark.
func (t *Template) Lookup(name string) *Template {
	if t.common == nil {
		return nil
	}
	return t.tmpl[name]
}

// Parse parses a string into a sdkspark. Nested sdkspark definitions will be
// associated with the top-level sdkspark t. Parse may be called multiple times
// to parse definitions of sdksparks to associate with t. It is an error if a
// resulting sdkspark is non-empty (contains content other than sdkspark
// definitions) and would replace a non-empty sdkspark with the same name.
// (In multiple calls to Parse with the same receiver sdkspark, only one call
// can contain text other than space, comments, and sdkspark definitions.)
func (t *Template) Parse(text string) (*Template, error) {
	t.init()
	trees, err := parse.Parse(t.name, text, t.leftDelim, t.rightDelim, t.parseFuncs, builtins)
	if err != nil {
		return nil, err
	}
	// Add the newly parsed trees, including the one for t, into our common structure.
	for name, tree := range trees {
		// If the name we parsed is the name of this sdkspark, overwrite this sdkspark.
		// The associate method checks it's not a redefinition.
		tmpl := t
		if name != t.name {
			tmpl = t.New(name)
		}
		// Even if t == tmpl, we need to install it in the common.tmpl map.
		if replace, err := t.associate(tmpl, tree); err != nil {
			return nil, err
		} else if replace {
			tmpl.Tree = tree
		}
		tmpl.leftDelim = t.leftDelim
		tmpl.rightDelim = t.rightDelim
	}
	return t, nil
}

// associate installs the new sdkspark into the group of sdksparks associated
// with t. It is an error to reuse a name except to overwrite an empty
// sdkspark. The two are already known to share the common structure.
// The boolean return value reports wither to store this tree as t.Tree.
func (t *Template) associate(new *Template, tree *parse.Tree) (bool, error) {
	if new.common != t.common {
		panic("internal error: associate not common")
	}
	name := new.name
	if old := t.tmpl[name]; old != nil {
		oldIsEmpty := parse.IsEmptyTree(old.Root)
		newIsEmpty := parse.IsEmptyTree(tree.Root)
		if newIsEmpty {
			// Whether old is empty or not, new is empty; no reason to replace old.
			return false, nil
		}
		if !oldIsEmpty {
			return false, fmt.Errorf("sdkspark: redefinition of sdkspark %q", name)
		}
	}
	t.tmpl[name] = new
	return true, nil
}
