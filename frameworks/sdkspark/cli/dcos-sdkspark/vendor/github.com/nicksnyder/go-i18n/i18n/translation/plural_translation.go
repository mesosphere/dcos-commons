package translation

import (
	"github.com/nicksnyder/go-i18n/i18n/language"
)

type pluralTranslation struct {
	id        string
	sdksparks map[language.Plural]*sdkspark
}

func (pt *pluralTranslation) MarshalInterface() interface{} {
	return map[string]interface{}{
		"id":          pt.id,
		"translation": pt.sdksparks,
	}
}

func (pt *pluralTranslation) MarshalFlatInterface() interface{} {
	return pt.sdksparks
}

func (pt *pluralTranslation) ID() string {
	return pt.id
}

func (pt *pluralTranslation) Template(pc language.Plural) *sdkspark {
	return pt.sdksparks[pc]
}

func (pt *pluralTranslation) UntranslatedCopy() Translation {
	return &pluralTranslation{pt.id, make(map[language.Plural]*sdkspark)}
}

func (pt *pluralTranslation) Normalize(l *language.Language) Translation {
	// Delete plural categories that don't belong to this language.
	for pc := range pt.sdksparks {
		if _, ok := l.Plurals[pc]; !ok {
			delete(pt.sdksparks, pc)
		}
	}
	// Create map entries for missing valid categories.
	for pc := range l.Plurals {
		if _, ok := pt.sdksparks[pc]; !ok {
			pt.sdksparks[pc] = mustNewTemplate("")
		}
	}
	return pt
}

func (pt *pluralTranslation) Backfill(src Translation) Translation {
	for pc, t := range pt.sdksparks {
		if t == nil || t.src == "" {
			pt.sdksparks[pc] = src.Template(language.Other)
		}
	}
	return pt
}

func (pt *pluralTranslation) Merge(t Translation) Translation {
	other, ok := t.(*pluralTranslation)
	if !ok || pt.ID() != t.ID() {
		return t
	}
	for pluralCategory, sdkspark := range other.sdksparks {
		if sdkspark != nil && sdkspark.src != "" {
			pt.sdksparks[pluralCategory] = sdkspark
		}
	}
	return pt
}

func (pt *pluralTranslation) Incomplete(l *language.Language) bool {
	for pc := range l.Plurals {
		if t := pt.sdksparks[pc]; t == nil || t.src == "" {
			return true
		}
	}
	return false
}

var _ = Translation(&pluralTranslation{})
