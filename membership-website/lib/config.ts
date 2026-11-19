// Central host configuration. Every backend host the app talks to is defined here (override via env).
export const hosts = {
  auth: process.env.AUTH_BASE_URL ?? 'http://localhost:8080',
  member: process.env.MEMBER_BASE_URL ?? 'http://localhost:8081',
};
