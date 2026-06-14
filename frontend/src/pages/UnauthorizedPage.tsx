export default function UnauthorizedPage() {
  return (
    <div className="flex flex-col items-center justify-center min-h-[60vh] gap-4">
      <div className="text-6xl">🚫</div>
      <h1 className="text-2xl font-bold">Không có quyền truy cập</h1>
      <p className="text-muted-foreground">
        Bạn không có quyền truy cập trang này.
      </p>
    </div>
  )
}
