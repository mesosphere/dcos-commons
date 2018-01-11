// Copyright (c) 2014 Alex Kalyvitis

package mustache

import (
	"fmt"
	"io"
)

type parser struct {
	lexer *lexer
	buf   []token
	ast   []node
}

// read returns the next token from the lexer and advances the cursor. This
// token will not be available by the parser after it has been read.
func (p *parser) read() token {
	if len(p.buf) > 0 {
		r := p.buf[0]
		p.buf = p.buf[1:]
		return r
	}
	return p.lexer.token()
}

// readn returns the next n tokens from the lexer and advances the cursor. If it
// coundn't read all n tokens, for example if a tokenEOF was returned by the
// lexer, an error is returned and the returned slice will have all tokens read
// until that point, including tokenEOF.
func (p *parser) readn(n int) ([]token, error) {
	tokens := make([]token, 0, n) // make a slice capable of storing up to n tokens
	for i := 0; i < n; i++ {
		tokens = append(tokens, p.read())
		if tokens[i].typ == tokenEOF {
			return tokens, io.EOF
		}
	}
	return tokens, nil
}

// readt returns the tokens starting from the current position until the first
// match of t. Similar to readn it will return an error if a tokenEOF was
// returned by the lexer before a match was made.
func (p *parser) readt(t tokenType) ([]token, error) {
	var tokens []token
	for {
		token := p.read()
		tokens = append(tokens, token)
		switch token.typ {
		case tokenEOF:
			return tokens, fmt.Errorf("token %q not found", t)
		case t:
			return tokens, nil
		}
	}
}

// readv returns the tokens starting from the current position until the first
// match of t. A match is made only of t.typ and t.val are equal to the examined
// token.
func (p *parser) readv(t token) ([]token, error) {
	var tokens []token
	for {
		read, err := p.readt(t.typ)
		tokens = append(tokens, read...)
		if err != nil {
			return tokens, err
		}
		if len(read) > 0 && read[len(read)-1].val == t.val {
			break
		}
	}
	return tokens, nil
}

// peek returns the next token without advancing the cursor. Consecutive calls
// of peek would result in the same token being retuned. To advance the cursor,
// a read must be made.
func (p *parser) peek() token {
	if len(p.buf) > 0 {
		return p.buf[0]
	}
	t := p.lexer.token()
	p.buf = append(p.buf, t)
	return t
}

// peekn returns the next n tokens without advancing the cursor.
func (p *parser) peekn(n int) ([]token, error) {
	if len(p.buf) > n {
		return p.buf[:n], nil
	}
	for i := len(p.buf) - 1; i < n; i++ {
		t := p.lexer.token()
		p.buf = append(p.buf, t)
		if t.typ == tokenEOF {
			return p.buf, io.EOF
		}
	}
	return p.buf, nil
}

// peekt returns the tokens from the current postition until the first match of
// t. it will not advance the cursor.
func (p *parser) peekt(t tokenType) ([]token, error) {
	for i := 0; i < len(p.buf); i++ {
		switch p.buf[i].typ {
		case t:
			return p.buf[:i], nil
		case tokenEOF:
			return p.buf[:i], io.EOF
		}
	}
	for {
		token := p.lexer.token()
		p.buf = append(p.buf, token)
		switch token.typ {
		case t:
			return p.buf, nil
		case tokenEOF:
			return p.buf, io.EOF
		}
	}
}

func (p *parser) errorf(t token, format string, v ...interface{}) error {
	return fmt.Errorf("%d:%d syntax error: %s", t.line, t.col, fmt.Sprintf(format, v...))
}

// parse begins parsing based on tokens read from the lexer.
func (p *parser) parse() ([]node, error) {
	var nodes []node
loop:
	for {
		token := p.read()
		switch token.typ {
		case tokenEOF:
			break loop
		case tokenError:
			return nil, p.errorf(token, "%s", token.val)
		case tokenText:
			nodes = append(nodes, textNode(token.val))
		case tokenLeftDelim:
			node, err := p.parseTag()
			if err != nil {
				return nodes, err
			}
			nodes = append(nodes, node)
		case tokenRawStart:
			node, err := p.parseRawTag()
			if err != nil {
				return nodes, err
			}
			nodes = append(nodes, node)
		case tokenSetDelim:
			nodes = append(nodes, new(delimNode))
		}
	}
	return nodes, nil
}

// parseTag parses a beggining of a mustache tag. It is assumed that a leftDelim
// was already read by the parser.
func (p *parser) parseTag() (node, error) {
	token := p.read()
	switch token.typ {
	case tokenIdentifier:
		return p.parseVar(token, true)
	case tokenRawStart:
		return p.parseRawTag()
	case tokenRawAlt:
		return p.parseVar(p.read(), false)
	case tokenComment:
		return p.parseComment()
	case tokenSectionInverse:
		return p.parseSection(true)
	case tokenSectionStart:
		return p.parseSection(false)
	case tokenPartial:
		return p.parsePartial()
	}
	return nil, p.errorf(token, "unreachable code %s", token)
}

// parseRawTag parses a simple variable tag. It is assumed that the read from
// the parser should return an identifier.
func (p *parser) parseRawTag() (node, error) {
	t := p.read()
	if t.typ != tokenIdentifier {
		return nil, p.errorf(t, "unexpected token %s", t)
	}
	if next := p.read(); next.typ != tokenRawEnd {
		return nil, p.errorf(t, "unexpected token %s", t)
	}
	if next := p.read(); next.typ != tokenRightDelim {
		return nil, p.errorf(t, "unexpected token %s", t)
	}
	return &varNode{name: t.val, escape: false}, nil
}

// parseVar parses a simple variable tag. It is assumed that the read from the
// parser should return an identifier.
func (p *parser) parseVar(ident token, escape bool) (node, error) {
	if t := p.read(); t.typ != tokenRightDelim {
		return nil, p.errorf(t, "unexpected token %s", t)
	}
	return &varNode{name: ident.val, escape: escape}, nil
}

// parseComment parses a comment block. It is assumed that the next read should
// return a t_comment token.
func (p *parser) parseComment() (node, error) {
	var comment string
	for {
		t := p.read()
		switch t.typ {
		case tokenEOF:
			return nil, p.errorf(t, "unexpected token %s", t)
		case tokenError:
			return nil, p.errorf(t, t.val)
		case tokenRightDelim:
			return commentNode(comment), nil
		default:
			comment += t.val
		}
	}
}

// parseSection parses a section block. It is assumed that the next read should
// return a t_section token.
func (p *parser) parseSection(inverse bool) (node, error) {
	t := p.read()
	if t.typ != tokenIdentifier {
		return nil, p.errorf(t, "unexpected token %s", t)
	}
	if next := p.read(); next.typ != tokenRightDelim {
		return nil, p.errorf(t, "unexpected token %s", t)
	}
	var (
		tokens []token
		stack  = 1
	)
	for {
		read, err := p.readv(t)
		if err != nil {
			return nil, err
		}
		tokens = append(tokens, read...)
		if len(read) > 1 {
			// Check the token that preceeded the matching identifier. For
			// section start and inverse tokens we increase the stack, otherwise
			// decrease.
			tt := read[len(read)-2]
			switch {
			case tt.typ == tokenSectionStart || tt.typ == tokenSectionInverse:
				stack++
			case tt.typ == tokenSectionEnd:
				stack--
			}
		}
		if stack == 0 {
			break
		}
	}
	nodes, err := subParser(tokens[:len(tokens)-3]).parse()
	if err != nil {
		return nil, err
	}
	section := &sectionNode{
		name:     t.val,
		inverted: inverse,
		elems:    nodes,
	}
	return section, nil
}

// parsePartial parses a partial block. It is assumed that the next read should
// return a t_ident token.
func (p *parser) parsePartial() (node, error) {
	t := p.read()
	if t.typ != tokenIdentifier {
		return nil, p.errorf(t, "unexpected token %s", t)
	}
	if next := p.read(); next.typ != tokenRightDelim {
		return nil, p.errorf(t, "unexpected token %s", t)
	}
	return &partialNode{t.val}, nil
}

// newParser creates a new parser using the suppliad lexer.
func newParser(l *lexer) *parser {
	return &parser{lexer: l}
}

// subParser creates a new parser with a pre-defined token buffer.
func subParser(b []token) *parser {
	return &parser{buf: append(b, token{typ: tokenEOF})}
}
