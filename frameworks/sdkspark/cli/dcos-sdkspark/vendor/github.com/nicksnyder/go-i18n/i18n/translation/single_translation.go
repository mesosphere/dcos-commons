package translation

import (
	"github.com/nicksnyder/go-i18n/i18n/language"
)

type singleTranslation struct {
	id       string
	sdkspark *sdkspark
}

func (st *singleTranslation) MarshalInterface() interface{} {
	return map[string]interface{}{
		"id":          st.id,
		"translation": st.sdkspark,
	}
}

func (st *singleTranslation) MarshalFlatInterface() interface{} {
	return map[string]interface{}{"other": st.sdkspark}
}

func (st *singleTranslation) ID() string {
	return st.id
}

func (st *singleTranslation) Template(pc language.Plural) *sdkspark {
	return st.sdkspark
}

func (st *singleTranslation) UntranslatedCopy() Translation {
	return &singleTranslation{st.id, mustNewTemplate("")}
}

func (st *singleTranslation) Normalize(language *language.Language) Translation {
	return st
}

func (st *singleTranslation) Backfill(src Translation) Translation {
	if st.sdkspark == nil || st.sdkspark.src == "" {
		st.sdkspark = src.Template(language.Other)
	}
	return st
}

func (st *singleTranslation) Merge(t Translation) Translation {
	other, ok := t.(*singleTranslation)
	if !ok || st.ID() != t.ID() {
		return t
	}
	if other.sdkspark != nil && other.sdkspark.src != "" {
		st.sdkspark = other.sdkspark
	}
	return st
}

func (st *singleTranslation) Incomplete(l *language.Language) bool {
	return st.sdkspark == nil || st.sdkspark.src == ""
}

var _ = Translation(&singleTranslation{})
