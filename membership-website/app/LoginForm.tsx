"use client";

import { useState } from "react";
import { validateLogin } from "@/lib/validation";

type Props = { navigate?: (url: string) => void };

export default function LoginForm({
  navigate = (url) => window.location.assign(url),
}: Props) {
  const [values, setValues] = useState({ email: "", password: "" });
  const [touched, setTouched] = useState({ email: false, password: false });
  const [pending, setPending] = useState(false);
  const [result, setResult] = useState<{ ok: boolean; message: string } | null>(
    null,
  );

  const errors = validateLogin({
    email: values.email.trim(),
    password: values.password,
  });
  const hasErrors = Object.keys(errors).length > 0;
  const show = (k: "email" | "password") => touched[k] && errors[k];

  const onChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const { name, value } = e.target;
    setValues((v) => ({ ...v, [name]: value }));
    setTouched((t) => ({ ...t, [name]: true }));
  };

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (hasErrors) return;
    setPending(true);
    setResult(null);
    try {
      const res = await fetch("/api/login", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          email: values.email.trim(),
          password: values.password,
        }),
      });
      const data = await res.json().catch(() => ({}));
      if (res.ok && data.redirectTo) {
        navigate(data.redirectTo); // full navigation to /callback?code=... which exchanges it for tokens
      } else {
        setResult({ ok: false, message: data.message ?? "Login failed." });
      }
    } catch {
      setResult({ ok: false, message: "Network error. Please try again." });
    } finally {
      setPending(false);
    }
  };

  return (
    <form onSubmit={onSubmit} noValidate className="auth-form">
      <label className="field">
        Email
        <input
          name="email"
          type="email"
          value={values.email}
          onChange={onChange}
          aria-invalid={!!show("email")}
        />
        {show("email") && (
          <small role="alert" className="field-error">
            {errors.email}
          </small>
        )}
      </label>
      <label className="field">
        Password
        <input
          name="password"
          type="password"
          value={values.password}
          onChange={onChange}
          aria-invalid={!!show("password")}
        />
        {show("password") && (
          <small role="alert" className="field-error">
            {errors.password}
          </small>
        )}
      </label>
      <button
        type="submit"
        disabled={pending || hasErrors}
        className="btn-primary"
      >
        {pending ? "Logging in…" : "Login"}
      </button>
      {result && (
        <p
          role="status"
          className={`notice ${result.ok ? "notice-success" : "notice-error"}`}
        >
          {result.message}
        </p>
      )}
    </form>
  );
}
