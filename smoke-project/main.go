package main

import "fmt"

func twice(value int) int {
	result := value * 2
	return result
}

func main() {
	input := 21
	output := twice(input)
	fmt.Printf("smoke result=%d\n", output)
}
