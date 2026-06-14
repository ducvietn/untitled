/**
 * Utility for merging Tailwind class names conditionally.
 * Automatically drops falsy values.
 */
import { type ClassValue, clsx } from 'clsx'
import { twMerge } from 'tailwind-merge'

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}
