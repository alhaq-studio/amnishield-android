// Ambient type declarations for Deno runtime in Supabase Edge Functions

declare namespace Deno {
  export interface Env {
    get(key: string): string | undefined;
    set(key: string, value: string): void;
  }
  export const env: Env;
  export function serve(handler: (req: Request) => Response | Promise<Response>): void;
  export function test(name: string, fn: () => void | Promise<void>): void;
}

declare module "https://*" {
  export const assertEquals: (actual: any, expected: any, msg?: string) => void;
  export const assertNotEquals: (actual: any, expected: any, msg?: string) => void;
  export const serve: (handler: (req: Request) => Response | Promise<Response>) => void;
  const content: any;
  export default content;
}
