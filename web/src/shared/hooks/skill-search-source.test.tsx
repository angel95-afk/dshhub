// @vitest-environment jsdom
import { createElement, type ReactNode } from 'react'
import { act, cleanup, renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { afterEach, expect, it, vi } from 'vitest'
import { useSearchSkills } from './use-skill-queries'

const fetchJson = vi.hoisted(() => vi.fn())
vi.mock('@/api/client', () => ({ WEB_API_PREFIX: '/api/web', fetchJson }))
afterEach(cleanup)

/**
 * Retained from the removed source-filter test: a filter change must not leave the grid
 * empty mid-flight, and a failure must still surface as an error rather than as stale rows.
 */
it('keeps the previous page while the next filter loads and surfaces a failure', async () => {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const wrapper = ({ children }: { children: ReactNode }) => createElement(QueryClientProvider, { client }, children)
  let rejectNext!: (error: Error) => void
  fetchJson.mockImplementation((url: string) => url.includes('label=next')
    ? new Promise((_resolve, reject) => { rejectNext = reject })
    : Promise.resolve({ items: [{ id: 1, slug: 'first' }], total: 1, page: 0, size: 12 }))
  const hook = renderHook(({ label }: { label: string }) => {
    const result = useSearchSkills({ label, size: 12 })
    return { data: result.data, isError: result.isError, status: result.status, fetchStatus: result.fetchStatus }
  }, {
    wrapper, initialProps: { label: 'first' },
  })
  try {
    await waitFor(() => expect(hook.result.current.data?.items[0].slug).toBe('first'))
    hook.rerender({ label: 'next' })
    // The old rows stay visible instead of flashing a skeleton grid.
    expect(hook.result.current.data?.items[0].slug).toBe('first')
    await act(async () => rejectNext(new Error('Offline')))
    await waitFor(() => expect(hook.result.current.isError).toBe(true))
  } finally {
    hook.unmount()
    client.clear()
  }
})
