package main

import "testing"

func TestTwice(t *testing.T) {
	if got := twice(21); got != 42 {
		t.Fatalf("twice(21) = %d, want 42", got)
	}
}
