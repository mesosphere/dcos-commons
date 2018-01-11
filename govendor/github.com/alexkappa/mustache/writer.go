package mustache

import (
	"bufio"
	"bytes"
	"io"
)

type writer struct {
	hasText bool
	hasTag  bool
	w       io.Writer
	b       *bufio.Writer
}

func newWriter(w io.Writer) *writer {
	return &writer{
		hasText: false,
		hasTag:  false,
		w:       w,
		b:       bufio.NewWriter(w),
	}
}

func (w *writer) text() {
	w.hasText = true
}

func (w *writer) tag() {
	w.hasTag = true
}

func (w *writer) reset() {
	w.hasTag = false
	w.hasText = false
}

func (w *writer) flush() error {
	defer w.reset()
	if w.hasTag && !w.hasText {
		w.b.Reset(w.w)
		return nil
	}
	return w.b.Flush()
}

func (w *writer) write(r rune) error {
	_, err := w.b.WriteRune(r)
	if err != nil {
		return err
	}
	if r == '\n' {
		return w.flush()
	}
	return nil
}

func (w *writer) Write(b []byte) (int, error) {
	for i, r := range bytes.Runes(b) {
		err := w.write(r)
		if err != nil {
			return i, err
		}
	}
	return len(b), nil
}
