/**
 * Loads a part of the page that is fetched only when first opened. Null when its file cannot be
 * fetched: a deploy replaces the hashed files, so a page opened before it asks for one that is
 * gone, and only a reload gets the new page.
 */
export async function loadPart<T>(load: () => Promise<T>): Promise<T | null> {
  try {
    return await load();
  } catch {
    return null;
  }
}
