import React, { useEffect, useState } from 'react';
import { Spin } from 'antd';
import request from '@/api/request';

/** 以当前 JWT 拉取图片（条码/签名图等）并显示，src 为 /api 开头的完整路径 */
const AuthImage: React.FC<{ src: string; alt?: string; style?: React.CSSProperties }> = ({
  src,
  alt,
  style,
}) => {
  const [url, setUrl] = useState<string>();

  useEffect(() => {
    let revoke: string | undefined;
    let active = true;
    const path = src.startsWith('/api/v1') ? src.slice('/api/v1'.length) : src;
    request
      .get(path, { responseType: 'blob' })
      .then((blob) => {
        if (!active) return;
        revoke = window.URL.createObjectURL(new Blob([blob as unknown as BlobPart]));
        setUrl(revoke);
      })
      .catch(() => undefined);
    return () => {
      active = false;
      if (revoke) window.URL.revokeObjectURL(revoke);
    };
  }, [src]);

  if (!url) return <Spin size="small" />;
  return <img src={url} alt={alt} style={style} />;
};

export default AuthImage;
